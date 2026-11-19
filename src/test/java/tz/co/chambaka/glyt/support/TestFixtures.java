package tz.co.chambaka.glyt.support;

import tz.co.chambaka.glyt.config.AppProperties;
import tz.co.chambaka.glyt.config.NotificationServiceProperties;
import tz.co.chambaka.glyt.domain.Location;
import tz.co.chambaka.glyt.domain.UserAccount;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.security.AuthUser;

import java.time.Duration;
import java.util.List;

public final class TestFixtures {

    private TestFixtures() {}

    public static AppProperties appProperties() {
        return new AppProperties(
                new AppProperties.Jwt(
                        "test-jwt-secret-must-be-at-least-32-bytes-long",
                        Duration.ofMinutes(15),
                        Duration.ofHours(8)
                ),
                new AppProperties.Cors(List.of("http://localhost")),
                new AppProperties.Storage("local", "target/test-uploads", "", "test", "k", "s", "auto", true),
                new AppProperties.Ops(
                        20,
                        2,
                        "http://localhost:8080",
                        "Test desk",
                        "Test place",
                        "06:00–22:00",
                        "+255 777 000 111",
                        "255777000111"
                ),
                new AppProperties.Seed(false),
                new AppProperties.Mail(true),
                new AppProperties.Payments(
                        true,
                        "test-webhook-secret",
                        "http://localhost:8080/api/v1/payments/sandbox",
                        "http://localhost:8080/api/v1/payments/sandbox"
                )
        );
    }

    public static NotificationServiceProperties notificationDisabled() {
        return new NotificationServiceProperties(false, "http://localhost:7575", "", "GOLYT", "");
    }

    public static Location location(String code) {
        Location location = new Location();
        location.setId(1L);
        location.setCode(code);
        location.setName(code + " Airport");
        location.setLocationType("AIRPORT");
        location.setActive(true);
        return location;
    }

    public static UserAccount staff(Role role, String locationCode) {
        UserAccount user = new UserAccount();
        user.setId(10L);
        user.setFullName("Test User");
        user.setEmail("test@golyt.co.tz");
        user.setPhone("+255712345678");
        user.setPasswordHash("$2a$10$hashed");
        user.setRole(role);
        user.setLocation(location(locationCode));
        user.setActive(true);
        user.setMustChangePassword(false);
        user.setTokenVersion(1);
        user.setFailedAttempts(0);
        return user;
    }

    public static AuthUser authUser(UserAccount account) {
        return new AuthUser(
                account.getId(),
                account.getEmail(),
                account.getFullName(),
                account.getRole(),
                account.locationCode(),
                account.getTokenVersion(),
                account.isMustChangePassword()
        );
    }
}
