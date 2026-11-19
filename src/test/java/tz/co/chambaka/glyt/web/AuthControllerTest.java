package tz.co.chambaka.glyt.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import tz.co.chambaka.glyt.service.AuthService;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ChangePasswordRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ForgotRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.LoginRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.LoginResponse;
import tz.co.chambaka.glyt.web.dto.GlytDtos.MessageResponse;
import tz.co.chambaka.glyt.web.dto.GlytDtos.RefreshRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.ResetRequest;
import tz.co.chambaka.glyt.web.dto.GlytDtos.UserView;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    AuthService auth;
    @Mock
    LoginResponse loginResponse;
    @Mock
    UserView userView;
    @InjectMocks
    AuthController controller;

    @Test
    void loginDelegatesCredentials() {
        when(auth.login("a@b.co", "pw")).thenReturn(loginResponse);
        assertThat(controller.login(new LoginRequest("a@b.co", "pw"))).isSameAs(loginResponse);
    }

    @Test
    void loginPropagatesApiException() {
        when(auth.login("a@b.co", "bad")).thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "Invalid"));
        assertThatThrownBy(() -> controller.login(new LoginRequest("a@b.co", "bad")))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid");
    }

    @Test
    void refreshDelegatesToken() {
        when(auth.refresh("rt")).thenReturn(loginResponse);
        assertThat(controller.refresh(new RefreshRequest("rt"))).isSameAs(loginResponse);
    }

    @Test
    void logoutWithTokenRevokesIt() {
        MessageResponse response = controller.logout(new RefreshRequest("rt"));
        verify(auth).logout("rt");
        assertThat(response.message()).isEqualTo("Signed out.");
        assertThat(response.resetToken()).isNull();
    }

    @Test
    void logoutWithoutBodyPassesNull() {
        MessageResponse response = controller.logout(null);
        verify(auth).logout(null);
        assertThat(response.message()).isEqualTo("Signed out.");
    }

    @Test
    void logoutAllRevokesEverySession() {
        MessageResponse response = controller.logoutAll();
        verify(auth).logoutAll();
        assertThat(response.message()).isEqualTo("Signed out on every device.");
    }

    @Test
    void changePasswordDelegates() {
        when(auth.changePassword("old", "new")).thenReturn(loginResponse);
        assertThat(controller.changePassword(new ChangePasswordRequest("old", "new"))).isSameAs(loginResponse);
    }

    @Test
    void forgotDelegates() {
        MessageResponse expected = new MessageResponse("sent", "tok");
        when(auth.forgot("a@b.co")).thenReturn(expected);
        assertThat(controller.forgot(new ForgotRequest("a@b.co"))).isSameAs(expected);
    }

    @Test
    void resetDelegates() {
        MessageResponse expected = new MessageResponse("done", null);
        when(auth.reset("tok", "pw")).thenReturn(expected);
        assertThat(controller.reset(new ResetRequest("tok", "pw"))).isSameAs(expected);
    }

    @Test
    void meReturnsCurrentUser() {
        when(auth.me()).thenReturn(userView);
        assertThat(controller.me()).isSameAs(userView);
    }
}
