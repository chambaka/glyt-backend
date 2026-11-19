package tz.co.chambaka.glyt.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import tz.co.chambaka.glyt.config.AppProperties;
import tz.co.chambaka.glyt.domain.PasswordResetToken;
import tz.co.chambaka.glyt.domain.RefreshToken;
import tz.co.chambaka.glyt.domain.UserAccount;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.repo.PasswordResetTokenRepository;
import tz.co.chambaka.glyt.repo.RefreshTokenRepository;
import tz.co.chambaka.glyt.repo.UserAccountRepository;
import tz.co.chambaka.glyt.security.JwtService;
import tz.co.chambaka.glyt.support.ServiceTestData;
import tz.co.chambaka.glyt.support.TestFixtures;
import tz.co.chambaka.glyt.web.ApiException;
import tz.co.chambaka.glyt.web.dto.GlytDtos.LoginResponse;
import tz.co.chambaka.glyt.web.dto.GlytDtos.MessageResponse;
import tz.co.chambaka.glyt.web.dto.GlytDtos.UserView;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    private static final String GOOD_PASSWORD = "Str0ng!Passw0rd";

    @Mock UserAccountRepository users;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock PasswordResetTokenRepository resetTokens;
    @Mock PasswordEncoder passwords;
    @Mock SmsGatewayClient sms;

    private AppProperties properties;
    private JwtService jwt;
    private AuthService service;
    private UserAccount user;

    @BeforeEach
    void setUp() {
        properties = TestFixtures.appProperties();
        jwt = new JwtService(properties);
        service = new AuthService(users, refreshTokens, resetTokens, passwords, jwt, properties, sms);
        user = TestFixtures.staff(Role.KIA_OFFICER, "KIA");
        when(passwords.encode(anyString())).thenReturn("ENCODED");
    }

    @AfterEach
    void tearDown() {
        ServiceTestData.clearAuth();
    }

    private void rebuild(AppProperties props) {
        properties = props;
        service = new AuthService(users, refreshTokens, resetTokens, passwords, new JwtService(props), props, sms);
    }

    private static void assertApi(Runnable action, HttpStatus status, String message) {
        ApiException ex = ServiceTestData.expectApi(action);
        assertThat(ex.status()).isEqualTo(status);
        assertThat(ex.getMessage()).contains(message);
    }

    // ---- login ----

    @Test
    void loginSucceedsAndResetsCounters() {
        user.setFailedAttempts(3);
        user.setLockedUntil(Instant.now().minusSeconds(60));
        when(users.findByEmailIgnoreCase("test@golyt.co.tz")).thenReturn(Optional.of(user));
        when(passwords.matches("secret", "$2a$10$hashed")).thenReturn(true);

        LoginResponse response = service.login("  test@golyt.co.tz ", "secret");

        assertThat(response.token()).isNotBlank();
        assertThat(response.refreshToken()).hasSize(64);
        assertThat(response.expiresIn()).isEqualTo(Duration.ofMinutes(15).toSeconds());
        assertThat(response.user().email()).isEqualTo("test@golyt.co.tz");
        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getLastLoginAt()).isNotNull();
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isEqualTo(AuthService.hash(response.refreshToken()));
        assertThat(saved.getValue().getUser()).isSameAs(user);
    }

    @Test
    void loginRejectsUnknownEmail() {
        when(users.findByEmailIgnoreCase("")).thenReturn(Optional.empty());
        assertApi(() -> service.login(null, "x"), HttpStatus.UNAUTHORIZED, "Email or password");
        assertApi(() -> service.login("nobody@x.com", "x"), HttpStatus.UNAUTHORIZED, "Email or password");
    }

    @Test
    void loginRejectsDisabledAccount() {
        user.setActive(false);
        when(users.findByEmailIgnoreCase("test@golyt.co.tz")).thenReturn(Optional.of(user));
        assertApi(() -> service.login("test@golyt.co.tz", "x"), HttpStatus.FORBIDDEN, "disabled");
    }

    @Test
    void loginRejectsLockedAccount() {
        user.setLockedUntil(Instant.now().plusSeconds(600));
        when(users.findByEmailIgnoreCase("test@golyt.co.tz")).thenReturn(Optional.of(user));
        assertApi(() -> service.login("test@golyt.co.tz", "x"), HttpStatus.FORBIDDEN, "locked");
    }

    @Test
    void loginWrongPasswordIncrementsAttempts() {
        when(users.findByEmailIgnoreCase("test@golyt.co.tz")).thenReturn(Optional.of(user));
        when(passwords.matches(anyString(), anyString())).thenReturn(false);

        assertApi(() -> service.login("test@golyt.co.tz", "bad"), HttpStatus.UNAUTHORIZED, "Email or password");

        assertThat(user.getFailedAttempts()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void loginWithNullPasswordIsTreatedAsEmpty() {
        when(users.findByEmailIgnoreCase("test@golyt.co.tz")).thenReturn(Optional.of(user));
        when(passwords.matches("", "$2a$10$hashed")).thenReturn(false);
        assertApi(() -> service.login("test@golyt.co.tz", null), HttpStatus.UNAUTHORIZED, "Email or password");
        verify(passwords).matches("", "$2a$10$hashed");
    }

    @Test
    void loginLocksAccountOnFifthFailure() {
        user.setFailedAttempts(4);
        when(users.findByEmailIgnoreCase("test@golyt.co.tz")).thenReturn(Optional.of(user));
        when(passwords.matches(anyString(), anyString())).thenReturn(false);

        assertApi(() -> service.login("test@golyt.co.tz", "bad"), HttpStatus.UNAUTHORIZED, "Email or password");

        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLockedUntil()).isAfter(Instant.now().plusSeconds(14 * 60));
    }

    // ---- refresh ----

    @Test
    void refreshRotatesToken() {
        RefreshToken stored = storedToken(Instant.now().plusSeconds(3600), null);
        when(refreshTokens.findByTokenHash(AuthService.hash("raw-token"))).thenReturn(Optional.of(stored));

        LoginResponse response = service.refresh(" raw-token ");

        assertThat(stored.getRevokedAt()).isNotNull();
        assertThat(response.refreshToken()).isNotEqualTo("raw-token");
        verify(refreshTokens).save(any(RefreshToken.class));
    }

    @Test
    void refreshRequiresToken() {
        assertApi(() -> service.refresh(null), HttpStatus.BAD_REQUEST, "Refresh token is required");
        assertApi(() -> service.refresh("  "), HttpStatus.BAD_REQUEST, "Refresh token is required");
    }

    @Test
    void refreshRejectsUnknownToken() {
        when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.empty());
        assertApi(() -> service.refresh("nope"), HttpStatus.UNAUTHORIZED, "Sign in");
    }

    @Test
    void refreshRejectsRevokedToken() {
        RefreshToken stored = storedToken(Instant.now().plusSeconds(3600), Instant.now().minusSeconds(5));
        when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.of(stored));
        assertApi(() -> service.refresh("raw"), HttpStatus.UNAUTHORIZED, "Sign in");
    }

    @Test
    void refreshRejectsExpiredToken() {
        RefreshToken stored = storedToken(Instant.now().minusSeconds(5), null);
        when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.of(stored));
        assertApi(() -> service.refresh("raw"), HttpStatus.UNAUTHORIZED, "Sign in");
    }

    @Test
    void refreshRejectsDisabledUser() {
        user.setActive(false);
        RefreshToken stored = storedToken(Instant.now().plusSeconds(3600), null);
        when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.of(stored));
        assertApi(() -> service.refresh("raw"), HttpStatus.FORBIDDEN, "disabled");
        assertThat(stored.getRevokedAt()).isNull();
    }

    private RefreshToken storedToken(Instant expires, Instant revoked) {
        RefreshToken stored = new RefreshToken();
        stored.setUser(user);
        stored.setExpiresAt(expires);
        stored.setRevokedAt(revoked);
        return stored;
    }

    // ---- logout ----

    @Test
    void logoutIgnoresBlankToken() {
        service.logout(null);
        service.logout("   ");
        verify(refreshTokens, never()).findByTokenHash(anyString());
    }

    @Test
    void logoutRevokesKnownToken() {
        RefreshToken stored = storedToken(Instant.now().plusSeconds(60), null);
        when(refreshTokens.findByTokenHash(AuthService.hash("abc"))).thenReturn(Optional.of(stored));
        service.logout(" abc ");
        assertThat(stored.getRevokedAt()).isNotNull();
    }

    @Test
    void logoutWithUnknownTokenIsNoop() {
        when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.empty());
        service.logout("unknown");
        verify(refreshTokens).findByTokenHash(AuthService.hash("unknown"));
    }

    @Test
    void logoutAllBumpsTokenVersionAndDeletesRefreshTokens() {
        ServiceTestData.authenticate(user);
        when(users.findById(10L)).thenReturn(Optional.of(user));
        service.logoutAll();
        assertThat(user.getTokenVersion()).isEqualTo(2);
        verify(refreshTokens).deleteByUserId(10L);
    }

    @Test
    void logoutAllRequiresExistingUser() {
        ServiceTestData.authenticate(user);
        when(users.findById(10L)).thenReturn(Optional.empty());
        assertApi(service::logoutAll, HttpStatus.UNAUTHORIZED, "Sign in");
    }

    @Test
    void logoutAllRequiresAuthentication() {
        assertApi(service::logoutAll, HttpStatus.UNAUTHORIZED, "Sign in");
    }

    // ---- change password ----

    @Test
    void changePasswordSucceedsAndSendsSms() {
        ServiceTestData.authenticate(user);
        when(users.findById(10L)).thenReturn(Optional.of(user));
        when(passwords.matches("old", "$2a$10$hashed")).thenReturn(true);
        user.setMustChangePassword(true);

        LoginResponse response = service.changePassword("old", GOOD_PASSWORD);

        assertThat(user.getPasswordHash()).isEqualTo("ENCODED");
        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(user.getTokenVersion()).isEqualTo(2);
        assertThat(response.token()).isNotBlank();
        verify(refreshTokens).deleteByUserId(10L);
        verify(sms).queueSms(eq("+255712345678"), contains("+255 777 000 111"), eq("GoLyt password changed"),
                eq("GOLYT_PASSWORD_CHANGED"), contains("golyt-pwd-changed-10-"));
    }

    @Test
    void changePasswordSmsFallsBackWhenDeskPhoneBlank() {
        AppProperties.Ops base = TestFixtures.appProperties().ops();
        rebuild(ServiceTestData.withOps(new AppProperties.Ops(base.overweightKg(), base.uncollectedDays(),
                base.publicBaseUrl(), base.pickupName(), base.pickupPlace(), base.pickupHours(), "", "")));
        ServiceTestData.authenticate(user);
        when(users.findById(10L)).thenReturn(Optional.of(user));
        when(passwords.matches(anyString(), anyString())).thenReturn(true);

        service.changePassword("old", GOOD_PASSWORD);

        verify(sms).queueSms(anyString(), contains("contact your operations lead"), anyString(), anyString(), anyString());
    }

    @Test
    void changePasswordSkipsSmsWithoutPhone() {
        user.setPhone(null);
        ServiceTestData.authenticate(user);
        when(users.findById(10L)).thenReturn(Optional.of(user));
        when(passwords.matches(anyString(), anyString())).thenReturn(true);

        service.changePassword("old", GOOD_PASSWORD);

        verify(sms, never()).queueSms(any(), any(), any(), any(), any());
    }

    @Test
    void changePasswordRequiresKnownUser() {
        ServiceTestData.authenticate(user);
        when(users.findById(10L)).thenReturn(Optional.empty());
        assertApi(() -> service.changePassword("old", GOOD_PASSWORD), HttpStatus.UNAUTHORIZED, "Sign in");
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        ServiceTestData.authenticate(user);
        when(users.findById(10L)).thenReturn(Optional.of(user));
        when(passwords.matches(anyString(), anyString())).thenReturn(false);
        assertApi(() -> service.changePassword(null, GOOD_PASSWORD), HttpStatus.BAD_REQUEST, "Current password");
        verify(passwords).matches("", "$2a$10$hashed");
    }

    @Test
    void changePasswordRejectsWeakNewPassword() {
        ServiceTestData.authenticate(user);
        when(users.findById(10L)).thenReturn(Optional.of(user));
        when(passwords.matches(anyString(), anyString())).thenReturn(true);
        assertApi(() -> service.changePassword("old", "short"), HttpStatus.BAD_REQUEST, "at least 10");
        assertApi(() -> service.changePassword("old", null), HttpStatus.BAD_REQUEST, "Password is required");
        verify(refreshTokens, never()).deleteByUserId(any());
    }

    // ---- forgot ----

    @Test
    void forgotIssuesSmsCodeAndExposesTokenWhenConfigured() {
        when(users.findByEmailIgnoreCase("test@golyt.co.tz")).thenReturn(Optional.of(user));
        when(sms.queueSms(any(), any(), any(), any(), any())).thenReturn(true);

        MessageResponse response = service.forgot(" test@golyt.co.tz ");

        assertThat(response.message()).contains("SMS reset code");
        assertThat(response.resetToken()).hasSize(5);
        verify(resetTokens).deleteByUserId(10L);
        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(resetTokens).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isEqualTo(AuthService.hash(response.resetToken()));
        assertThat(saved.getValue().getExpiresAt()).isAfter(Instant.now().plusSeconds(9 * 60));
        verify(sms).queueSms(eq("+255712345678"), contains(response.resetToken()), eq("GoLyt password reset"),
                eq("GOLYT_PASSWORD_RESET"), contains("golyt-reset-10-"));
    }

    @Test
    void forgotHidesTokenWhenNotExposed() {
        rebuild(ServiceTestData.withMail(false));
        when(users.findByEmailIgnoreCase("test@golyt.co.tz")).thenReturn(Optional.of(user));

        MessageResponse response = service.forgot("test@golyt.co.tz");

        assertThat(response.resetToken()).isNull();
        verify(resetTokens).save(any(PasswordResetToken.class));
        verify(sms).queueSms(any(), any(), any(), any(), any());
    }

    @Test
    void forgotIsGenericForUnknownEmail() {
        when(users.findByEmailIgnoreCase("")).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase("ghost@x.com")).thenReturn(Optional.empty());

        assertThat(service.forgot(null).resetToken()).isNull();
        assertThat(service.forgot("ghost@x.com").message()).contains("If that email is registered");
        verify(resetTokens, never()).save(any());
    }

    @Test
    void forgotIgnoresInactiveUser() {
        user.setActive(false);
        when(users.findByEmailIgnoreCase("test@golyt.co.tz")).thenReturn(Optional.of(user));
        assertThat(service.forgot("test@golyt.co.tz").resetToken()).isNull();
        verify(sms, never()).queueSms(any(), any(), any(), any(), any());
    }

    @Test
    void forgotSkipsUsersWithoutPhone() {
        user.setPhone("not a phone");
        when(users.findByEmailIgnoreCase("test@golyt.co.tz")).thenReturn(Optional.of(user));
        assertThat(service.forgot("test@golyt.co.tz").resetToken()).isNull();
        verify(resetTokens, never()).save(any());
    }

    // ---- reset ----

    @Test
    void resetUpdatesPasswordAndMarksTokenUsed() {
        PasswordResetToken token = resetToken(Instant.now().plusSeconds(300), null);
        user.setFailedAttempts(3);
        user.setLockedUntil(Instant.now().plusSeconds(100));
        user.setMustChangePassword(true);
        when(resetTokens.findByTokenHash(AuthService.hash("AB3CD"))).thenReturn(Optional.of(token));

        MessageResponse response = service.reset(" ab-3 cd ", GOOD_PASSWORD);

        assertThat(response.message()).contains("Password updated");
        assertThat(response.resetToken()).isNull();
        assertThat(user.getPasswordHash()).isEqualTo("ENCODED");
        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(user.getTokenVersion()).isEqualTo(2);
        assertThat(token.getUsedAt()).isNotNull();
        verify(refreshTokens).deleteByUserId(10L);
        verify(sms).queueSms(any(), contains("password was changed"), any(), eq("GOLYT_PASSWORD_CHANGED"), any());
    }

    @Test
    void resetRequiresCode() {
        assertApi(() -> service.reset(null, GOOD_PASSWORD), HttpStatus.BAD_REQUEST, "Reset code is required");
    }

    @Test
    void resetRejectsUnknownCode() {
        when(resetTokens.findByTokenHash(anyString())).thenReturn(Optional.empty());
        assertApi(() -> service.reset("ZZZZZ", GOOD_PASSWORD), HttpStatus.BAD_REQUEST, "not valid");
    }

    @Test
    void resetRejectsUsedCode() {
        when(resetTokens.findByTokenHash(anyString())).thenReturn(Optional.of(resetToken(Instant.now().plusSeconds(60), Instant.now())));
        assertApi(() -> service.reset("ZZZZZ", GOOD_PASSWORD), HttpStatus.BAD_REQUEST, "not valid");
    }

    @Test
    void resetRejectsExpiredCode() {
        when(resetTokens.findByTokenHash(anyString())).thenReturn(Optional.of(resetToken(Instant.now().minusSeconds(60), null)));
        assertApi(() -> service.reset("ZZZZZ", GOOD_PASSWORD), HttpStatus.BAD_REQUEST, "not valid");
    }

    @Test
    void resetRejectsWeakPassword() {
        when(resetTokens.findByTokenHash(anyString())).thenReturn(Optional.of(resetToken(Instant.now().plusSeconds(60), null)));
        assertThatThrownBy(() -> service.reset("ZZZZZ", "weak")).isInstanceOf(ApiException.class);
    }

    private PasswordResetToken resetToken(Instant expires, Instant used) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setExpiresAt(expires);
        token.setUsedAt(used);
        return token;
    }

    // ---- me / toView ----

    @Test
    void meReturnsCurrentUserView() {
        ServiceTestData.authenticate(user);
        when(users.findById(10L)).thenReturn(Optional.of(user));
        UserView view = service.me();
        assertThat(view.id()).isEqualTo(10L);
        assertThat(view.role()).isEqualTo("KIA_OFFICER");
        assertThat(view.location()).isEqualTo("KIA");
    }

    @Test
    void meRequiresKnownUser() {
        ServiceTestData.authenticate(user);
        when(users.findById(10L)).thenReturn(Optional.empty());
        assertApi(service::me, HttpStatus.UNAUTHORIZED, "Sign in");
    }

    @Test
    void toViewReportsAllLocationsForUsersWithoutLocation() {
        user.setLocation(null);
        assertThat(AuthService.toView(user).location()).isEqualTo("ALL");
    }
}
