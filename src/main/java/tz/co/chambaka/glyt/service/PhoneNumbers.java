package tz.co.chambaka.glyt.service;

import org.springframework.http.HttpStatus;
import tz.co.chambaka.glyt.web.ApiException;

/** Tanzania phone normalisation helpers (Nexus / notification-service style). */
public final class PhoneNumbers {
    private PhoneNumbers() {}

    public static String required(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A valid Tanzania phone number is required.");
        }
        return normalized;
    }

    public static String optional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return required(value);
    }

    /** Returns E.164-like {@code +255…} or null when invalid. */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.trim().replaceAll("[^0-9+]", "");
        if (digits.startsWith("+")) {
            digits = digits.substring(1);
        }
        if (digits.startsWith("0") && digits.length() == 10) {
            digits = "255" + digits.substring(1);
        } else if (digits.length() == 9) {
            digits = "255" + digits;
        }
        if (!digits.matches("255[167]\\d{8}")) {
            return null;
        }
        return "+" + digits;
    }
}
