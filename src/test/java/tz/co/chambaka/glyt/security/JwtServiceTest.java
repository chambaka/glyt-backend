package tz.co.chambaka.glyt.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;
import tz.co.chambaka.glyt.config.AppProperties;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.support.TestFixtures;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-jwt-secret-must-be-at-least-32-bytes-long";

    private final AppProperties properties = TestFixtures.appProperties();
    private final JwtService service = new JwtService(properties);

    private static AppProperties withJwt(AppProperties base, AppProperties.Jwt jwt) {
        return new AppProperties(jwt, base.cors(), base.storage(), base.ops(), base.seed(), base.mail(), base.payments());
    }

    private static SecretKey key() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void generateAndParseRoundTrip() {
        AuthUser user = new AuthUser(42L, "kia@golyt.co.tz", "John Mrema", Role.KIA_OFFICER, "KIA", 3, true);

        AuthUser parsed = service.parse(service.generate(user));

        assertThat(parsed).isEqualTo(user);
    }

    @Test
    void roundTripPreservesFixtureUser() {
        AuthUser user = TestFixtures.authUser(TestFixtures.staff(Role.ADMIN, "ZNZ"));

        assertThat(service.parse(service.generate(user))).isEqualTo(user);
    }

    @Test
    void roundTripCustomerWithNullLocation() {
        AuthUser user = new AuthUser(7L, "c@x.com", "Cust", Role.CUSTOMER, null, 0, false);

        AuthUser parsed = service.parse(service.generate(user));

        assertThat(parsed.location()).isNull();
        assertThat(parsed.role()).isEqualTo(Role.CUSTOMER);
        assertThat(parsed.mustChangePassword()).isFalse();
    }

    @Test
    void ttlSecondsReflectsConfiguration() {
        assertThat(service.ttlSeconds()).isEqualTo(15 * 60);
    }

    @Test
    void constructorRejectsShortSecret() {
        AppProperties shortSecret = withJwt(properties,
                new AppProperties.Jwt("too-short", Duration.ofMinutes(5), Duration.ofHours(1)));

        assertThatThrownBy(() -> new JwtService(shortSecret))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void constructorAcceptsExactly32Bytes() {
        AppProperties exact = withJwt(properties,
                new AppProperties.Jwt("0123456789abcdef0123456789abcdef", Duration.ofMinutes(5), Duration.ofHours(1)));

        assertThat(new JwtService(exact).ttlSeconds()).isEqualTo(300);
    }

    @Test
    void parseRejectsExpiredToken() {
        AppProperties expired = withJwt(properties,
                new AppProperties.Jwt(SECRET, Duration.ofSeconds(-60), Duration.ofHours(1)));
        JwtService expiringService = new JwtService(expired);
        String token = expiringService.generate(new AuthUser(1L, "a@b.co", "A", Role.ADMIN, "ALL", 0, false));

        assertThatThrownBy(() -> service.parse(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void parseRejectsTokenSignedWithDifferentKey() {
        AppProperties other = withJwt(properties,
                new AppProperties.Jwt("another-secret-that-is-at-least-32-bytes!!", Duration.ofMinutes(5), Duration.ofHours(1)));
        String token = new JwtService(other).generate(new AuthUser(1L, "a@b.co", "A", Role.ADMIN, "ALL", 0, false));

        assertThatThrownBy(() -> service.parse(token)).isInstanceOf(SignatureException.class);
    }

    @Test
    void parseRejectsGarbage() {
        assertThatThrownBy(() -> service.parse("not-a-jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    void parseRejectsEmptyToken() {
        assertThatThrownBy(() -> service.parse("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseRejectsTamperedPayload() {
        String token = service.generate(new AuthUser(1L, "a@b.co", "A", Role.READONLY, "ALL", 0, false));
        String[] parts = token.split("\\.");
        String forged = parts[0] + "." + parts[1] + "x." + parts[2];

        assertThatThrownBy(() -> service.parse(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void parseRejectsUnsignedToken() {
        String unsigned = Jwts.builder().subject("1").claim("role", "ADMIN").compact();

        assertThatThrownBy(() -> service.parse(unsigned)).isInstanceOf(JwtException.class);
    }

    @Test
    void parseDefaultsMissingVersionAndMustChangeFlag() {
        String token = Jwts.builder()
                .subject("9")
                .claim("email", "e@x.com")
                .claim("name", "Name")
                .claim("role", "ZNZ_OFFICER")
                .claim("location", "ZNZ")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key())
                .compact();

        AuthUser parsed = service.parse(token);

        assertThat(parsed.id()).isEqualTo(9L);
        assertThat(parsed.tokenVersion()).isZero();
        assertThat(parsed.mustChangePassword()).isFalse();
        assertThat(parsed.role()).isEqualTo(Role.ZNZ_OFFICER);
    }

    @Test
    void parseRejectsUnknownRole() {
        String token = Jwts.builder()
                .subject("9")
                .claim("role", "WIZARD")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key())
                .compact();

        assertThatThrownBy(() -> service.parse(token)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseRejectsNonNumericSubject() {
        String token = Jwts.builder()
                .subject("abc")
                .claim("role", "ADMIN")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key())
                .compact();

        assertThatThrownBy(() -> service.parse(token)).isInstanceOf(NumberFormatException.class);
    }
}
