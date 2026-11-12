package tz.co.chambaka.glyt.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, Storage storage, Ops ops, Seed seed, Mail mail, Payments payments) {

    public record Jwt(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {}

    public record Cors(List<String> allowedOrigins) {
        public Cors {
            if (allowedOrigins == null) {
                allowedOrigins = new ArrayList<>();
            }
        }
    }

    public record Storage(
            String type,
            String uploadDir,
            String endpoint,
            String bucket,
            String accessKey,
            String secretKey,
            String region,
            Boolean pathStyle
    ) {}

    public record Ops(
            double overweightKg,
            int uncollectedDays,
            String publicBaseUrl,
            String pickupName,
            String pickupPlace,
            String pickupHours,
            String pickupPhone,
            String pickupWhatsapp
    ) {}

    public record Seed(boolean enabled) {}

    public record Mail(boolean exposeResetToken) {}

    public record Payments(
            boolean sandbox,
            String webhookSecret,
            String mobileMoneyCheckoutBase,
            String cardCheckoutBase
    ) {}
}
