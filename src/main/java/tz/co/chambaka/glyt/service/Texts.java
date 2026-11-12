package tz.co.chambaka.glyt.service;

import org.springframework.http.HttpStatus;
import tz.co.chambaka.glyt.web.ApiException;

import java.time.LocalDate;

public final class Texts {
    private Texts() {}

    public static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + " is required.");
        }
        return value.trim();
    }

    public static String optional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public static String date(String value, String label) {
        String parsed = required(value, label);
        try {
            LocalDate.parse(parsed);
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + " must be a date (YYYY-MM-DD).");
        }
        return parsed;
    }

    public static String password(String value) {
        return password(value, null, null);
    }

    /** Staff / portal passwords use the same Nexus complexity rules. */
    public static String password(String value, String email, String name) {
        return StaffPasswordPolicy.validate(required(value, "Password"), email, name);
    }

    public static String maskPassport(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 4) {
            return "••••";
        }
        return "•".repeat(trimmed.length() - 4) + trimmed.substring(trimmed.length() - 4);
    }

    public static <E extends Enum<E>> E parse(Class<E> type, String value, String label) {
        String raw = required(value, label);
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase().replace(' ', '_').replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, label + " is not recognised.");
        }
    }
}
