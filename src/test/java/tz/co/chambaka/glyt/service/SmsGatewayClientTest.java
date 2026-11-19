package tz.co.chambaka.glyt.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tz.co.chambaka.glyt.config.NotificationServiceProperties;
import tz.co.chambaka.glyt.support.TestFixtures;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the disabled / empty-key / invalid-input guards with plain objects and the HTTP paths against an
 * in-process JDK {@link HttpServer} (no Mockito needed: the client builds its own RestClient).
 */
class SmsGatewayClientTest {

    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastApiKey = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile int status = 200;
    private volatile String responseBody = "{\"error\":false}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            lastPath.set(exchange.getRequestURI().getPath());
            lastApiKey.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private SmsGatewayClient client(boolean enabled, String apiKey, String provider) {
        return new SmsGatewayClient(new NotificationServiceProperties(enabled, baseUrl() + "/", apiKey, "GOLYT", provider));
    }

    private JsonNode sentJson() throws IOException {
        return new ObjectMapper().readTree(lastBody.get());
    }

    // ---- guards ----

    @Test
    void disabledGatewayDoesNotCallTheNetwork() {
        SmsGatewayClient disabled = new SmsGatewayClient(TestFixtures.notificationDisabled());
        assertThat(disabled.queueSms("+255712345678", "hello", "s", "t", "k")).isFalse();
        assertThat(client(false, "key", "").queueSms("+255712345678", "hello", "s", "t", "k")).isFalse();
        assertThat(calls.get()).isZero();
    }

    @Test
    void emptyApiKeyDoesNotCallTheNetwork() {
        assertThat(client(true, "", "").queueSms("+255712345678", "hello", "s", "t", "k")).isFalse();
        assertThat(client(true, "   ", "").queueSms("+255712345678", "hello", "s", "t", "k")).isFalse();
        assertThat(client(true, null, "").queueSms("+255712345678", "hello", "s", "t", "k")).isFalse();
        assertThat(calls.get()).isZero();
    }

    @Test
    void invalidPhoneOrBlankBodyIsRejectedLocally() {
        SmsGatewayClient enabled = client(true, "key", "");
        assertThat(enabled.queueSms("not-a-phone", "hello", "s", "t", "k")).isFalse();
        assertThat(enabled.queueSms(null, "hello", "s", "t", "k")).isFalse();
        assertThat(enabled.queueSms("+255712345678", " ", "s", "t", "k")).isFalse();
        assertThat(enabled.queueSms("+255712345678", null, "s", "t", "k")).isFalse();
        assertThat(calls.get()).isZero();
    }

    @Test
    void defaultsAreAppliedByNotificationProperties() {
        NotificationServiceProperties defaults = new NotificationServiceProperties(true, " ", null, " ", null);
        assertThat(defaults.baseUrl()).isEqualTo("http://localhost:7575");
        assertThat(defaults.apiKey()).isEmpty();
        assertThat(defaults.senderName()).isEqualTo("GOLYT");
        assertThat(defaults.provider()).isEmpty();
        // The client constructs fine with a default base URL and short-circuits on the empty key.
        assertThat(new SmsGatewayClient(defaults).queueSms("0712345678", "hi", null, null, null)).isFalse();
    }

    // ---- HTTP ----

    @Test
    void successfulQueueSendsFullPayload() throws Exception {
        boolean queued = client(true, " secret ", " twilio ").queueSms("0712 345 678", "hello", "Subject", "TAG", "idem-1");

        assertThat(queued).isTrue();
        assertThat(lastPath.get()).isEqualTo("/f1/queueNotification");
        assertThat(lastApiKey.get()).isEqualTo("secret");
        JsonNode json = sentJson();
        assertThat(json.get("type").asText()).isEqualTo("SMS");
        assertThat(json.get("to").asText()).isEqualTo("+255712345678");
        assertThat(json.get("body").asText()).isEqualTo("hello");
        assertThat(json.get("senderName").asText()).isEqualTo("GOLYT");
        assertThat(json.get("from").asText()).isEqualTo("GOLYT");
        assertThat(json.get("subject").asText()).isEqualTo("Subject");
        assertThat(json.get("tag").asText()).isEqualTo("TAG");
        assertThat(json.get("idempotencyKey").asText()).isEqualTo("idem-1");
        assertThat(json.get("provider").asText()).isEqualTo("twilio");
    }

    @Test
    void optionalFieldsAreOmittedWhenBlank() throws Exception {
        assertThat(client(true, "key", "").queueSms("+255712345678", "hello", " ", null, "")).isTrue();
        JsonNode json = sentJson();
        assertThat(json.has("subject")).isFalse();
        assertThat(json.has("tag")).isFalse();
        assertThat(json.has("idempotencyKey")).isFalse();
        assertThat(json.has("provider")).isFalse();
    }

    @Test
    void longIdempotencyKeyIsTruncatedToOneHundredCharacters() throws Exception {
        String key = "k".repeat(150);
        assertThat(client(true, "key", "").queueSms("+255712345678", "hello", null, null, key)).isTrue();
        assertThat(sentJson().get("idempotencyKey").asText()).hasSize(100);
    }

    @Test
    void emptyResponseBodyCountsAsQueued() {
        responseBody = "";
        assertThat(client(true, "key", "").queueSms("+255712345678", "hello", null, null, null)).isTrue();
    }

    @Test
    void gatewayErrorFlagReturnsFalse() {
        responseBody = "{\"error\":true,\"errorMessage\":\"no credit\"}";
        assertThat(client(true, "key", "").queueSms("+255712345678", "hello", null, "TAG", null)).isFalse();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void httpFailureReturnsFalse() {
        status = 500;
        responseBody = "{\"error\":\"boom\"}";
        assertThat(client(true, "key", "").queueSms("+255712345678", "hello", null, null, null)).isFalse();
    }

    @Test
    void unreachableGatewayReturnsFalse() {
        int port = server.getAddress().getPort();
        server.stop(0);
        SmsGatewayClient dead = new SmsGatewayClient(
                new NotificationServiceProperties(true, "http://127.0.0.1:" + port, "key", "GOLYT", ""));
        assertThat(dead.queueSms("+255712345678", "hello", null, null, null)).isFalse();
    }
}
