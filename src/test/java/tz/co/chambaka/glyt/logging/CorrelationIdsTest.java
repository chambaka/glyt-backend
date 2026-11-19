package tz.co.chambaka.glyt.logging;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdsTest {

    @Test
    void constantsHaveExpectedValues() {
        assertThat(CorrelationIds.HEADER).isEqualTo("X-Correlation-Id");
        assertThat(CorrelationIds.REQUEST_ID_HEADER).isEqualTo("X-Request-Id");
        assertThat(CorrelationIds.MDC_CORRELATION_ID).isEqualTo("correlationId");
        assertThat(CorrelationIds.MDC_USER_ID).isEqualTo("userId");
        assertThat(CorrelationIds.MDC_USER_ROLE).isEqualTo("userRole");
        assertThat(CorrelationIds.MDC_REQUEST_METHOD).isEqualTo("requestMethod");
        assertThat(CorrelationIds.MDC_REQUEST_PATH).isEqualTo("requestPath");
    }

    @Test
    void utilityClassIsNotInstantiable() throws Exception {
        Constructor<CorrelationIds> ctor = CorrelationIds.class.getDeclaredConstructor();
        assertThat(Modifier.isPrivate(ctor.getModifiers())).isTrue();
        ctor.setAccessible(true);
        assertThat(ctor.newInstance()).isNotNull();
    }
}
