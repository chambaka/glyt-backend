package tz.co.chambaka.glyt.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import tz.co.chambaka.glyt.config.AppProperties;
import tz.co.chambaka.glyt.domain.enums.Role;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final AppProperties properties;
    private final SecretKey key;

    public JwtService(AppProperties properties) {
        this.properties = properties;
        byte[] secretBytes = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public String generate(AuthUser user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.jwt().accessTokenTtl());
        return Jwts.builder()
                .subject(String.valueOf(user.id()))
                .claim("email", user.email())
                .claim("name", user.fullName())
                .claim("role", user.role().name())
                .claim("location", user.location())
                .claim("ver", user.tokenVersion())
                .claim("mustChangePassword", user.mustChangePassword())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public long ttlSeconds() {
        return properties.jwt().accessTokenTtl().toSeconds();
    }

    public AuthUser parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        Integer version = claims.get("ver", Integer.class);
        Boolean mustChange = claims.get("mustChangePassword", Boolean.class);
        return new AuthUser(
                Long.parseLong(claims.getSubject()),
                claims.get("email", String.class),
                claims.get("name", String.class),
                Role.valueOf(claims.get("role", String.class)),
                claims.get("location", String.class),
                version == null ? 0 : version,
                mustChange != null && mustChange
        );
    }
}
