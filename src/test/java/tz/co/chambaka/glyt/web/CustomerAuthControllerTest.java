package tz.co.chambaka.glyt.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import tz.co.chambaka.glyt.service.CustomerAuthService;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CustomerAuthResponse;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CustomerProfileView;
import tz.co.chambaka.glyt.web.dto.GlytDtos.CustomerRegisterRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.LoginRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.RefreshRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerAuthControllerTest {

    @Mock
    CustomerAuthService auth;
    @Mock
    CustomerAuthResponse authResponse;
    @InjectMocks
    CustomerAuthController controller;

    @Test
    void registerDelegates() {
        CustomerRegisterRequest request = mock(CustomerRegisterRequest.class);
        when(auth.register(request)).thenReturn(authResponse);
        assertThat(controller.register(request)).isSameAs(authResponse);
    }

    @Test
    void registerPropagatesConflict() {
        CustomerRegisterRequest request = mock(CustomerRegisterRequest.class);
        when(auth.register(request)).thenThrow(new ApiException(HttpStatus.CONFLICT, "exists"));
        assertThatThrownBy(() -> controller.register(request))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.status()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void loginDelegates() {
        when(auth.login("a@b.co", "pw")).thenReturn(authResponse);
        assertThat(controller.login(new LoginRequest("a@b.co", "pw"))).isSameAs(authResponse);
    }

    @Test
    void refreshDelegates() {
        when(auth.refresh("rt")).thenReturn(authResponse);
        assertThat(controller.refresh(new RefreshRequest("rt"))).isSameAs(authResponse);
    }

    @Test
    void logoutWithToken() {
        Map<String, String> body = controller.logout(new RefreshRequest("rt"));
        verify(auth).logout("rt");
        assertThat(body).containsEntry("message", "Signed out.");
    }

    @Test
    void logoutWithoutBody() {
        Map<String, String> body = controller.logout(null);
        verify(auth).logout(null);
        assertThat(body).containsEntry("message", "Signed out.");
    }

    @Test
    void meReturnsProfile() {
        CustomerProfileView profile = mock(CustomerProfileView.class);
        when(auth.me()).thenReturn(profile);
        assertThat(controller.me()).isSameAs(profile);
    }
}
