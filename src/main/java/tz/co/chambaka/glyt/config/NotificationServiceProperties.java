package tz.co.chambaka.glyt.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notification")
public record NotificationServiceProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String senderName,
        String provider
) {
    public NotificationServiceProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:7575";
        }
        if (apiKey == null) {
            apiKey = "";
        }
        if (senderName == null || senderName.isBlank()) {
            senderName = "GOLYT";
        }
        if (provider == null) {
            provider = "";
        }
    }
}
