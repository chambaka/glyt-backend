package tz.co.chambaka.glyt.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private final Map<String, String> seenInChain = new HashMap<>();

    private final FilterChain capturingChain = (req, res) -> {
        Map<String, String> copy = MDC.getCopyOfContextMap();
        if (copy != null) {
            seenInChain.putAll(copy);
        }
    };

    @BeforeEach
    void setUp() {
        MDC.clear();
        request = new MockHttpServletRequest("POST", "/api/v1/bags");
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void generatesIdWhenNoHeaderPresent() throws Exception {
        filter.doFilter(request, response, capturingChain);

        String generated = response.getHeader(CorrelationIds.HEADER);
        assertThat(generated).isNotBlank().hasSize(32).matches("[0-9a-f]{32}");
        assertThat(seenInChain).containsEntry(CorrelationIds.MDC_CORRELATION_ID, generated);
    }

    @Test
    void generatedIdsAreUnique() throws Exception {
        filter.doFilter(request, response, capturingChain);
        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/x"), second, capturingChain);

        assertThat(response.getHeader(CorrelationIds.HEADER)).isNotEqualTo(second.getHeader(CorrelationIds.HEADER));
    }

    @Test
    void reusesCorrelationHeaderWhenSafe() throws Exception {
        request.addHeader(CorrelationIds.HEADER, "  abc-123_DEF.456  ");

        filter.doFilter(request, response, capturingChain);

        assertThat(response.getHeader(CorrelationIds.HEADER)).isEqualTo("abc-123_DEF.456");
        assertThat(seenInChain).containsEntry(CorrelationIds.MDC_CORRELATION_ID, "abc-123_DEF.456");
    }

    @Test
    void fallsBackToRequestIdHeader() throws Exception {
        request.addHeader(CorrelationIds.REQUEST_ID_HEADER, "req-id-12345");

        filter.doFilter(request, response, capturingChain);

        assertThat(response.getHeader(CorrelationIds.HEADER)).isEqualTo("req-id-12345");
    }

    @Test
    void correlationHeaderWinsOverRequestIdHeader() throws Exception {
        request.addHeader(CorrelationIds.HEADER, "corr-id-12345");
        request.addHeader(CorrelationIds.REQUEST_ID_HEADER, "req-id-12345");

        filter.doFilter(request, response, capturingChain);

        assertThat(response.getHeader(CorrelationIds.HEADER)).isEqualTo("corr-id-12345");
    }

    @Test
    void blankCorrelationHeaderFallsThroughToRequestId() throws Exception {
        request.addHeader(CorrelationIds.HEADER, "   ");
        request.addHeader(CorrelationIds.REQUEST_ID_HEADER, "req-id-12345");

        filter.doFilter(request, response, capturingChain);

        assertThat(response.getHeader(CorrelationIds.HEADER)).isEqualTo("req-id-12345");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "short",                       // < 8 chars
            "has space in it",             // illegal char
            "bad/char/slash",              // illegal char
            "semi;colon;value",            // illegal char
            "new\\nline-injection"         // illegal char
    })
    void unsafeHeaderValuesAreReplaced(String unsafe) throws Exception {
        request.addHeader(CorrelationIds.HEADER, unsafe);

        filter.doFilter(request, response, capturingChain);

        String id = response.getHeader(CorrelationIds.HEADER);
        assertThat(id).isNotEqualTo(unsafe).matches("[0-9a-f]{32}");
    }

    @Test
    void overlongHeaderIsReplaced() throws Exception {
        request.addHeader(CorrelationIds.HEADER, "a".repeat(129));

        filter.doFilter(request, response, capturingChain);

        assertThat(response.getHeader(CorrelationIds.HEADER)).matches("[0-9a-f]{32}");
    }

    @Test
    void maxLengthHeaderAccepted() throws Exception {
        String max = "a".repeat(128);
        request.addHeader(CorrelationIds.HEADER, max);

        filter.doFilter(request, response, capturingChain);

        assertThat(response.getHeader(CorrelationIds.HEADER)).isEqualTo(max);
    }

    @Test
    void minLengthHeaderAccepted() throws Exception {
        request.addHeader(CorrelationIds.HEADER, "12345678");

        filter.doFilter(request, response, capturingChain);

        assertThat(response.getHeader(CorrelationIds.HEADER)).isEqualTo("12345678");
    }

    @Test
    void putsRequestMethodAndPathInMdcDuringChain() throws Exception {
        request.setRequestURI("/api/v1/bags/42");

        filter.doFilter(request, response, capturingChain);

        assertThat(seenInChain)
                .containsEntry(CorrelationIds.MDC_REQUEST_METHOD, "POST")
                .containsEntry(CorrelationIds.MDC_REQUEST_PATH, "/api/v1/bags/42");
    }

    @Test
    void mdcIsClearedAfterRequest() throws Exception {
        MDC.put("leftover", "value");

        filter.doFilter(request, response, capturingChain);

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void mdcIsClearedEvenWhenChainThrows() {
        FilterChain failing = (req, res) -> {
            throw new ServletException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, failing))
                .isInstanceOf(ServletException.class)
                .hasMessage("boom");

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
        assertThat(response.getHeader(CorrelationIds.HEADER)).isNotBlank();
    }

    @Test
    void ioExceptionPropagatesAndMdcCleared() {
        FilterChain failing = (req, res) -> {
            throw new IOException("disk");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, failing)).isInstanceOf(IOException.class);
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
