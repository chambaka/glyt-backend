package tz.co.chambaka.glyt.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.web.ApiException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserTest {

    @BeforeEach
    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsAuthUserPrincipal() {
        AuthUser user = new AuthUser(1L, "a@b.co", "A", Role.ADMIN, "ALL", 0, false);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        assertThat(CurrentUser.get()).isSameAs(user);
    }

    @Test
    void throwsUnauthorizedWhenNoAuthentication() {
        assertThatThrownBy(CurrentUser::get)
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getMessage()).isEqualTo("Sign in to continue.");
                });
    }

    @Test
    void throwsUnauthorizedWhenPrincipalIsNotAuthUser() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "plain-string-principal", null, List.of()));

        assertThatThrownBy(CurrentUser::get)
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.status()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void throwsUnauthorizedForAnonymousToken() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThatThrownBy(CurrentUser::get).isInstanceOf(ApiException.class);
    }

    @Test
    void utilityClassHasPrivateConstructor() throws Exception {
        Constructor<CurrentUser> ctor = CurrentUser.class.getDeclaredConstructor();
        assertThat(Modifier.isPrivate(ctor.getModifiers())).isTrue();
        ctor.setAccessible(true);
        assertThat(ctor.newInstance()).isNotNull();
    }
}
