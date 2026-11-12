package tz.co.chambaka.glyt.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tz.co.chambaka.glyt.config.NotificationServiceProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dispatches SMS via chambaka-notification-service ({@code POST /f1/queueNotification}),
 * the same platform Soleil Nexus uses.
 */
@Component
public class SmsGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(SmsGatewayClient.class);

    private final NotificationServiceProperties properties;
    private final RestClient restClient;

    public SmsGatewayClient(NotificationServiceProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl().replaceAll("/$", ""))
                .build();
    }

    public boolean queueSms(String to, String body, String subject, String tag, String idempotencyKey) {
        if (!properties.enabled()) {
            log.debug("SMS gateway disabled; not queueing to={}", to);
            return false;
        }
        if (!StringUtils.hasText(properties.apiKey())) {
            log.warn("app.notification.api-key is empty; SMS not queued for to={}", to);
            return false;
        }
        String phone = PhoneNumbers.normalize(to);
        if (phone == null || !StringUtils.hasText(body)) {
            return false;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "SMS");
        payload.put("to", phone);
        payload.put("body", body);
        payload.put("senderName", properties.senderName());
        payload.put("from", properties.senderName());
        if (StringUtils.hasText(subject)) {
            payload.put("subject", subject);
        }
        if (StringUtils.hasText(tag)) {
            payload.put("tag", tag);
        }
        if (StringUtils.hasText(idempotencyKey)) {
            payload.put("idempotencyKey", idempotencyKey.length() <= 100
                    ? idempotencyKey
                    : idempotencyKey.substring(0, 100));
        }
        if (StringUtils.hasText(properties.provider())) {
            payload.put("provider", properties.provider().trim());
        }

        try {
            Map<?, ?> response = restClient.post()
                    .uri("/f1/queueNotification")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-API-Key", properties.apiKey().trim())
                    .body(payload)
                    .retrieve()
                    .body(Map.class);
            boolean error = response != null && Boolean.TRUE.equals(response.get("error"));
            if (error) {
                log.warn("SMS queue rejected to={} tag={} message={}", phone, tag, response.get("errorMessage"));
                return false;
            }
            log.info("SMS queued remotely to={} tag={}", phone, tag);
            return true;
        } catch (Exception ex) {
            log.warn("SMS queue failed to={} tag={}: {}", phone, tag, ex.getMessage());
            return false;
        }
    }
}
