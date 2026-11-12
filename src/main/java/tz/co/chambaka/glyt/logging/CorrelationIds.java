package tz.co.chambaka.glyt.logging;

public final class CorrelationIds {

    public static final String HEADER = "X-Correlation-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String MDC_USER_ID = "userId";
    public static final String MDC_USER_ROLE = "userRole";
    public static final String MDC_REQUEST_METHOD = "requestMethod";
    public static final String MDC_REQUEST_PATH = "requestPath";

    private CorrelationIds() {}
}
