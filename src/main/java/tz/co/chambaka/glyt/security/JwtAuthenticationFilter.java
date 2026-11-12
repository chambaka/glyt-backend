package tz.co.chambaka.glyt.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.logging.CorrelationIds;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AccountLookup accounts;
    private final ObjectMapper json;

    public JwtAuthenticationFilter(JwtService jwtService, AccountLookup accounts, ObjectMapper json) {
        this.jwtService = jwtService;
        this.accounts = accounts;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                AuthUser parsed = jwtService.parse(header.substring(7));
                AuthUser user = accounts.load(parsed);
                if (user == null) {
                    SecurityContextHolder.clearContext();
                } else {
                    if (user.role() != Role.CUSTOMER && user.mustChangePassword() && !passwordChangeAllowed(request)) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        json.writeValue(response.getWriter(), Map.of("error", "Change your password before continuing."));
                        return;
                    }
                    var auth = new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()))
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    MDC.put(CorrelationIds.MDC_USER_ID, String.valueOf(user.id()));
                    MDC.put(CorrelationIds.MDC_USER_ROLE, user.role().name());
                }
            } catch (JwtException | IllegalArgumentException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean passwordChangeAllowed(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.endsWith("/api/v1/auth/change-password")
                || path.endsWith("/api/v1/auth/logout")
                || path.endsWith("/api/v1/auth/logout-all")
                || path.endsWith("/api/v1/auth/me");
    }
}
