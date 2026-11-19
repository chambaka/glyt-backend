package tz.co.chambaka.glyt.logging;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingFilterConfigTest {

    private final LoggingFilterConfig config = new LoggingFilterConfig();

    @Test
    void correlationIdRegistrationIsDisabled() {
        CorrelationIdFilter filter = new CorrelationIdFilter();

        FilterRegistrationBean<CorrelationIdFilter> registration = config.correlationIdFilterRegistration(filter);

        assertThat(registration.isEnabled()).isFalse();
        assertThat(registration.getFilter()).isSameAs(filter);
    }

    @Test
    void requestLoggingRegistrationIsDisabled() {
        RequestLoggingFilter filter = new RequestLoggingFilter();

        FilterRegistrationBean<RequestLoggingFilter> registration = config.requestLoggingFilterRegistration(filter);

        assertThat(registration.isEnabled()).isFalse();
        assertThat(registration.getFilter()).isSameAs(filter);
    }
}
