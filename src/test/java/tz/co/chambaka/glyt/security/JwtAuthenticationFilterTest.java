package tz.co.chambaka.glyt.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.logging.CorrelationIds;
import tz.co.chambaka.glyt.support.TestFixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    AccountLookup accounts;

    private final JwtService jwt = new JwtService(TestFixtures.appProperties());
    private final ObjectMapper json = new ObjectMapper();
    private JwtAuthenticationFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwt, accounts, json);
        request = new MockHttpServletRequest("GET", "/api/v1/bags");
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    private static AuthUser staff(boolean mustChange) {
        return new AuthUser(10L, "test@golyt.co.tz", "Test User", Role.KIA_OFFICER, "KIA", 1, mustChange);
    }

    private String bearer(AuthUser user) {
        return "Bearer " + jwt.generate(user);
    }

    @Test
    void noAuthorizationHeaderPassesThroughAnonymously() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(accounts);
    }

    @Test
    void nonBearerSchemeIsIgnored() throws Exception {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(accounts);
    }

    @Test
    void validTokenAuthenticatesAndPopulatesMdc() throws Exception {
        AuthUser parsed = staff(false);
        request.addHeader("Authorization", bearer(parsed));
        when(accounts.load(parsed)).thenReturn(parsed);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(parsed);
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_KIA_OFFICER");
        assertThat(MDC.get(CorrelationIds.MDC_USER_ID)).isEqualTo("10");
        assertThat(MDC.get(CorrelationIds.MDC_USER_ROLE)).isEqualTo("KIA_OFFICER");
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void authoritiesUseFreshRoleFromDatabase() throws Exception {
        AuthUser parsed = staff(false);
        AuthUser fresh = new AuthUser(10L, "test@golyt.co.tz", "Test User", Role.ADMIN, "ALL", 1, false);
        request.addHeader("Authorization", bearer(parsed));
        when(accounts.load(parsed)).thenReturn(fresh);

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString).containsExactly("ROLE_ADMIN");
    }

    @Test
    void customerWithMustChangeFlagIsNotBlocked() throws Exception {
        AuthUser customer = new AuthUser(5L, "c@x.com", "Cust", Role.CUSTOMER, null, 0, true);
        request.addHeader("Authorization", bearer(customer));
        when(accounts.load(customer)).thenReturn(customer);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(customer);
    }

    @Test
    void unknownAccountClearsContextButContinuesChain() throws Exception {
        AuthUser parsed = staff(false);
        request.addHeader("Authorization", bearer(parsed));
        when(accounts.load(parsed)).thenReturn(null);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(MDC.get(CorrelationIds.MDC_USER_ID)).isNull();
    }

    @Test
    void invalidTokenIsIgnoredAndChainContinues() throws Exception {
        request.addHeader("Authorization", "Bearer garbage.token.value");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getStatus()).isEqualTo(200);
        verifyNoInteractions(accounts);
    }

    @Test
    void emptyBearerTokenIsIgnored() throws Exception {
        request.addHeader("Authorization", "Bearer ");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void invalidTokenClearsPreexistingContext() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("x", null));
        request.addHeader("Authorization", "Bearer bad");

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void mustChangePasswordBlocksOrdinaryEndpoints() throws Exception {
        AuthUser user = staff(true);
        request.addHeader("Authorization", bearer(user));
        when(accounts.load(user)).thenReturn(user);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        JsonNode body = json.readTree(response.getContentAsString());
        assertThat(body.get("error").asText()).isEqualTo("Change your password before continuing.");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(chain);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/auth/change-password",
            "/api/v1/auth/logout",
            "/api/v1/auth/logout-all",
            "/api/v1/auth/me",
            "/glyt/api/v1/auth/me"
    })
    void mustChangePasswordAllowsAuthSelfServiceEndpoints(String path) throws Exception {
        AuthUser user = staff(true);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
        req.setRequestURI(path);
        req.addHeader("Authorization", bearer(user));
        when(accounts.load(user)).thenReturn(user);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(req);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(user);
    }

    @Test
    void mustChangePasswordBlocksSimilarButDifferentPaths() throws Exception {
        AuthUser user = staff(true);
        request.setRequestURI("/api/v1/auth/me/extra");
        request.addHeader("Authorization", bearer(user));
        when(accounts.load(user)).thenReturn(user);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verify(chain, org.mockito.Mockito.never()).doFilter(any(), any());
    }
}
