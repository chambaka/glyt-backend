package tz.co.chambaka.glyt.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request);
        MDC.put(CorrelationIds.MDC_CORRELATION_ID, correlationId);
        MDC.put(CorrelationIds.MDC_REQUEST_METHOD, request.getMethod());
        MDC.put(CorrelationIds.MDC_REQUEST_PATH, request.getRequestURI());
        response.setHeader(CorrelationIds.HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String fromHeader = firstNonBlank(
                request.getHeader(CorrelationIds.HEADER),
                request.getHeader(CorrelationIds.REQUEST_ID_HEADER)
        );
        if (fromHeader != null && isSafeId(fromHeader)) {
            return fromHeader.trim();
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean isSafeId(String value) {
        String trimmed = value.trim();
        if (trimmed.length() < 8 || trimmed.length() > 128) {
            return false;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.')) {
                return false;
            }
        }
        return true;
    }
}
