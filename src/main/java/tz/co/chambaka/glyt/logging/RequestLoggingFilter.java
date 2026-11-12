package tz.co.chambaka.glyt.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tz.co.chambaka.glyt.security.AuthUser;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health")
                || path.startsWith("/brand/")
                || path.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long started = System.currentTimeMillis();
        String method = request.getMethod();
        String path = request.getRequestURI();
        String query = request.getQueryString();
        String client = clientAddress(request);
        String contentType = request.getContentType();
        long contentLength = request.getContentLengthLong();

        log.info(
                "→ {} {}{} from={} contentType={} contentLength={}",
                method,
                path,
                query == null ? "" : "?" + query,
                client,
                contentType == null ? "-" : contentType,
                contentLength < 0 ? "-" : contentLength
        );
        try {
            filterChain.doFilter(request, response);
        } finally {
            enrichUserMdc();
            long tookMs = System.currentTimeMillis() - started;
            int status = response.getStatus();
            if (status >= 500) {
                log.error("← {} {} status={} took={}ms", method, path, status, tookMs);
            } else if (status >= 400) {
                log.warn("← {} {} status={} took={}ms", method, path, status, tookMs);
            } else {
                log.info("← {} {} status={} took={}ms", method, path, status, tookMs);
            }
        }
    }

    private void enrichUserMdc() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)) {
            return;
        }
        MDC.put(CorrelationIds.MDC_USER_ID, String.valueOf(user.id()));
        MDC.put(CorrelationIds.MDC_USER_ROLE, user.role().name());
    }

    private static String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        return request.getRemoteAddr();
    }
}
