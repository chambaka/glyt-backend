package tz.co.chambaka.glyt.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tz.co.chambaka.glyt.config.AppProperties;
import tz.co.chambaka.glyt.domain.PasswordResetToken;
import tz.co.chambaka.glyt.domain.RefreshToken;
import tz.co.chambaka.glyt.domain.UserAccount;
import tz.co.chambaka.glyt.repo.PasswordResetTokenRepository;
import tz.co.chambaka.glyt.repo.RefreshTokenRepository;
import tz.co.chambaka.glyt.repo.UserAccountRepository;
import tz.co.chambaka.glyt.security.AuthUser;
import tz.co.chambaka.glyt.security.CurrentUser;
import tz.co.chambaka.glyt.security.JwtService;
import tz.co.chambaka.glyt.web.ApiException;
import tz.co.chambaka.glyt.web.dto.GlytDtos.LoginResponse;
import tz.co.chambaka.glyt.web.dto.GlytDtos.MessageResponse;
import tz.co.chambaka.glyt.web.dto.GlytDtos.UserView;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String OTP_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final String GENERIC =
            "If that email is registered with a phone number, an SMS reset code was sent.";
    private static final String RESET_SMS_TAG = "GOLYT_PASSWORD_RESET";
    private static final String CHANGED_SMS_TAG = "GOLYT_PASSWORD_CHANGED";

    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final AppProperties properties;
    private final SmsGatewayClient sms;

    public AuthService(
            UserAccountRepository users,
            RefreshTokenRepository refreshTokens,
            PasswordResetTokenRepository resetTokens,
            PasswordEncoder passwords,
            JwtService jwt,
            AppProperties properties,
            SmsGatewayClient sms
    ) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.resetTokens = resetTokens;
        this.passwords = passwords;
        this.jwt = jwt;
        this.properties = properties;
        this.sms = sms;
    }

    @Transactional
    public LoginResponse login(String email, String password) {
        UserAccount user = users.findByEmailIgnoreCase(email == null ? "" : email.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Email or password is not correct."));
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This account is disabled. Contact your operations lead.");
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This account is locked. Try again in a few minutes.");
        }
        if (!passwords.matches(password == null ? "" : password, user.getPasswordHash())) {
            user.setFailedAttempts(user.getFailedAttempts() + 1);
            if (user.getFailedAttempts() >= 5) {
                user.setLockedUntil(Instant.now().plus(15, ChronoUnit.MINUTES));
                user.setFailedAttempts(0);
            }
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Email or password is not correct.");
        }
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        return issue(user);
    }

    @Transactional
    public LoginResponse refresh(String rawToken) {
        RefreshToken stored = refreshTokens.findByTokenHash(hash(Texts.required(rawToken, "Refresh token")))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sign in to continue."));
        if (stored.getRevokedAt() != null || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in to continue.");
        }
        UserAccount user = stored.getUser();
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This account is disabled. Contact your operations lead.");
        }
        stored.setRevokedAt(Instant.now());
        return issue(user);
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(hash(rawToken.trim())).ifPresent(token -> token.setRevokedAt(Instant.now()));
    }

    @Transactional
    public void logoutAll() {
        UserAccount user = users.findById(CurrentUser.get().id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sign in to continue."));
        user.setTokenVersion(user.getTokenVersion() + 1);
        refreshTokens.deleteByUserId(user.getId());
    }

    @Transactional
    public LoginResponse changePassword(String currentPassword, String newPassword) {
        UserAccount user = users.findById(CurrentUser.get().id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sign in to continue."));
        if (!passwords.matches(currentPassword == null ? "" : currentPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Current password is not correct.");
        }
        user.setPasswordHash(passwords.encode(Texts.password(newPassword, user.getEmail(), user.getFullName())));
        user.setMustChangePassword(false);
        user.setTokenVersion(user.getTokenVersion() + 1);
        refreshTokens.deleteByUserId(user.getId());
        queuePasswordChangedSms(user);
        return issue(user);
    }

    @Transactional
    public MessageResponse forgot(String email) {
        String token = users.findByEmailIgnoreCase(email == null ? "" : email.trim())
                .filter(UserAccount::isActive)
                .map(this::issueSmsReset)
                .orElse(null);
        return new MessageResponse(GENERIC, properties.mail().exposeResetToken() ? token : null);
    }

    @Transactional
    public MessageResponse reset(String rawToken, String password) {
        PasswordResetToken stored = resetTokens.findByTokenHash(hash(normalizeOtp(Texts.required(rawToken, "Reset code"))))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "This reset code is not valid."));
        if (stored.getUsedAt() != null || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This reset code is not valid.");
        }
        UserAccount user = stored.getUser();
        user.setPasswordHash(passwords.encode(Texts.password(password, user.getEmail(), user.getFullName())));
        user.setMustChangePassword(false);
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setTokenVersion(user.getTokenVersion() + 1);
        stored.setUsedAt(Instant.now());
        refreshTokens.deleteByUserId(user.getId());
        queuePasswordChangedSms(user);
        return new MessageResponse("Password updated. Sign in with the new password.", null);
    }

    public UserView me() {
        return toView(users.findById(CurrentUser.get().id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sign in to continue.")));
    }

    private String issueSmsReset(UserAccount user) {
        String phone = PhoneNumbers.normalize(user.getPhone());
        if (phone == null) {
            log.info("Password reset skipped for {} — no phone on file", user.getEmail());
            return null;
        }

        resetTokens.deleteByUserId(user.getId());
        String raw = generateOtp(5);
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(hash(raw));
        token.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        resetTokens.save(token);

        String body = "GoLyt: Tokeni Ya kubadilisha Neno la siri ni: " + raw
                + " (valid 10 minutes). If you did not request this, ignore the message.";
        boolean queued = sms.queueSms(
                phone,
                body,
                "GoLyt password reset",
                RESET_SMS_TAG,
                "golyt-reset-" + user.getId() + "-" + System.currentTimeMillis()
        );
        if (properties.mail().exposeResetToken()) {
            log.info("Password reset SMS for {} phone={} queued={} code={}", user.getEmail(), phone, queued, raw);
        } else {
            log.info("Password reset SMS for {} phone={} queued={}", user.getEmail(), phone, queued);
        }
        return raw;
    }

    private void queuePasswordChangedSms(UserAccount user) {
        String phone = PhoneNumbers.normalize(user.getPhone());
        if (phone == null) {
            return;
        }
        String desk = properties.ops().pickupPhone();
        String body = "GoLyt: Your password was changed successfully."
                + (StringUtils.hasText(desk)
                ? " If you did not do this, contact operations on " + desk + "."
                : " If you did not do this, contact your operations lead.");
        sms.queueSms(
                phone,
                body,
                "GoLyt password changed",
                CHANGED_SMS_TAG,
                "golyt-pwd-changed-" + user.getId() + "-" + System.currentTimeMillis()
        );
    }

    private LoginResponse issue(UserAccount user) {
        String rawRefresh = HexFormat.of().formatHex(RANDOM.generateSeed(32));
        RefreshToken refresh = new RefreshToken();
        refresh.setUser(user);
        refresh.setTokenHash(hash(rawRefresh));
        refresh.setExpiresAt(Instant.now().plus(properties.jwt().refreshTokenTtl()));
        refreshTokens.save(refresh);
        AuthUser principal = new AuthUser(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.locationCode(),
                user.getTokenVersion(),
                user.isMustChangePassword()
        );
        return new LoginResponse(jwt.generate(principal), rawRefresh, jwt.ttlSeconds(), user.isMustChangePassword(), toView(user));
    }

    public static UserView toView(UserAccount user) {
        return new UserView(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole().name(),
                user.locationCode(),
                user.isActive(),
                user.isMustChangePassword()
        );
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes()));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String generateOtp(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(OTP_ALPHABET.charAt(RANDOM.nextInt(OTP_ALPHABET.length())));
        }
        return sb.toString();
    }

    private static String normalizeOtp(String raw) {
        return raw.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
    }
}
