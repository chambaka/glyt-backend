package tz.co.chambaka.glyt.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.security.AuthUser;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;
    private Level previousLevel;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        request = new MockHttpServletRequest("GET", "/api/v1/bags");
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    private List<ILoggingEvent> events() {
        return appender.list;
    }

    private FilterChain statusChain(int status) {
        return (req, res) -> ((MockHttpServletResponse) res).setStatus(status);
    }

    // ---- shouldNotFilter ----

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/actuator/health/liveness", "/brand/logo.jpg", "/favicon.ico"})
    void skipsNoisyPaths(String path) {
        request.setRequestURI(path);
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/bags", "/actuator/info", "/brand", "/favicon.icon", "/"})
    void doesNotSkipNormalPaths(String path) {
        request.setRequestURI(path);
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void skippedPathProducesNoLogsButChainRuns() throws Exception {
        request.setRequestURI("/actuator/health");
        boolean[] called = {false};

        filter.doFilter(request, response, (req, res) -> called[0] = true);

        assertThat(called[0]).isTrue();
        assertThat(events()).isEmpty();
    }

    // ---- logging ----

    @Test
    void logsRequestAndResponseAtInfoForSuccess() throws Exception {
        request.setQueryString("page=2");
        request.setContentType("application/json");
        request.setContent("{\"a\":1}".getBytes());
        request.setRemoteAddr("10.0.0.5");

        filter.doFilter(request, response, statusChain(200));

        assertThat(events()).hasSize(2);
        ILoggingEvent in = events().get(0);
        ILoggingEvent out = events().get(1);
        assertThat(in.getLevel()).isEqualTo(Level.INFO);
        assertThat(in.getFormattedMessage())
                .contains("GET")
                .contains("/api/v1/bags?page=2")
                .contains("from=10.0.0.5")
                .contains("contentType=application/json")
                .contains("contentLength=7");
        assertThat(out.getLevel()).isEqualTo(Level.INFO);
        assertThat(out.getFormattedMessage()).contains("status=200").contains("took=");
    }

    @Test
    void logsDashesWhenNoQueryContentTypeOrLength() throws Exception {
        request.setRemoteAddr("10.0.0.5");

        filter.doFilter(request, response, statusChain(204));

        String line = events().get(0).getFormattedMessage();
        assertThat(line).contains("/api/v1/bags from=").doesNotContain("?");
        assertThat(line).contains("contentType=-").contains("contentLength=-");
    }

    @Test
    void logsWarnFor4xx() throws Exception {
        filter.doFilter(request, response, statusChain(404));

        assertThat(events().get(1).getLevel()).isEqualTo(Level.WARN);
        assertThat(events().get(1).getFormattedMessage()).contains("status=404");
    }

    @Test
    void logsWarnAt400Boundary() throws Exception {
        filter.doFilter(request, response, statusChain(400));

        assertThat(events().get(1).getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    void logsErrorFor5xx() throws Exception {
        filter.doFilter(request, response, statusChain(503));

        assertThat(events().get(1).getLevel()).isEqualTo(Level.ERROR);
        assertThat(events().get(1).getFormattedMessage()).contains("status=503");
    }

    @Test
    void logsInfoAt399() throws Exception {
        filter.doFilter(request, response, statusChain(399));

        assertThat(events().get(1).getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    void stillLogsResponseWhenChainThrows() {
        FilterChain failing = (req, res) -> {
            throw new ServletException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, failing)).isInstanceOf(ServletException.class);

        assertThat(events()).hasSize(2);
        assertThat(events().get(1).getFormattedMessage()).contains("status=200");
    }

    // ---- client address ----

    @Test
    void usesFirstForwardedForEntry() throws Exception {
        request.addHeader("X-Forwarded-For", "203.0.113.9, 70.41.3.18, 150.172.238.178");
        request.setRemoteAddr("10.0.0.5");

        filter.doFilter(request, response, statusChain(200));

        assertThat(events().get(0).getFormattedMessage()).contains("from=203.0.113.9 ");
    }

    @Test
    void singleForwardedForValueIsTrimmed() throws Exception {
        request.addHeader("X-Forwarded-For", "  198.51.100.7  ");

        filter.doFilter(request, response, statusChain(200));

        assertThat(events().get(0).getFormattedMessage()).contains("from=198.51.100.7 ");
    }

    @Test
    void blankForwardedForFallsBackToRemoteAddr() throws Exception {
        request.addHeader("X-Forwarded-For", "   ");
        request.setRemoteAddr("192.0.2.1");

        filter.doFilter(request, response, statusChain(200));

        assertThat(events().get(0).getFormattedMessage()).contains("from=192.0.2.1 ");
    }

    @Test
    void leadingCommaForwardedForIsKeptAsIs() throws Exception {
        request.addHeader("X-Forwarded-For", ",1.2.3.4");

        filter.doFilter(request, response, statusChain(200));

        assertThat(events().get(0).getFormattedMessage()).contains("from=,1.2.3.4 ");
    }

    // ---- MDC enrichment ----

    @Test
    void enrichesMdcWithAuthenticatedUser() throws Exception {
        AuthUser user = new AuthUser(77L, "a@b.co", "A", Role.ZNZ_OFFICER, "ZNZ", 0, false);
        FilterChain chain = (req, res) -> SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));

        filter.doFilter(request, response, chain);

        assertThat(MDC.get(CorrelationIds.MDC_USER_ID)).isEqualTo("77");
        assertThat(MDC.get(CorrelationIds.MDC_USER_ROLE)).isEqualTo("ZNZ_OFFICER");
    }

    @Test
    void doesNotTouchMdcWhenAnonymous() throws Exception {
        filter.doFilter(request, response, statusChain(200));

        assertThat(MDC.get(CorrelationIds.MDC_USER_ID)).isNull();
        assertThat(MDC.get(CorrelationIds.MDC_USER_ROLE)).isNull();
    }

    @Test
    void doesNotTouchMdcWhenPrincipalIsNotAuthUser() throws Exception {
        FilterChain chain = (req, res) -> SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("someone", null, List.of()));

        filter.doFilter(request, response, chain);

        assertThat(MDC.get(CorrelationIds.MDC_USER_ID)).isNull();
    }
}
