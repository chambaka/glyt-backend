package tz.co.chambaka.glyt.service;

import org.springframework.http.HttpStatus;
import tz.co.chambaka.glyt.web.ApiException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Staff password complexity rules aligned with Soleil Nexus. */
public final class StaffPasswordPolicy {

    public static final String SPECIAL_CHARS = "!@#$%^&*";

    private static final int MIN_LENGTH = 10;
    private static final Pattern UPPER = Pattern.compile("[A-Z]");
    private static final Pattern LOWER = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[!@#$%^&*]");

    private static final Set<String> COMMON_PASSWORDS =
            Set.of(
                    "password",
                    "password1",
                    "password12",
                    "password123",
                    "password1234",
                    "123456",
                    "1234567",
                    "12345678",
                    "123456789",
                    "1234567890",
                    "qwerty",
                    "qwerty123",
                    "abc123",
                    "111111",
                    "123123",
                    "admin",
                    "admin123",
                    "letmein",
                    "welcome",
                    "welcome1",
                    "monkey",
                    "dragon",
                    "master",
                    "login",
                    "princess",
                    "football",
                    "shadow",
                    "sunshine",
                    "iloveyou",
                    "trustno1",
                    "passw0rd",
                    "p@ssw0rd",
                    "p@ssword",
                    "changeme",
                    "secret",
                    "soleil",
                    "soleiltech",
                    "nexus",
                    "golyt",
                    "administrator",
                    "root",
                    "test1234",
                    "password1!",
                    "password123!",
                    "qwerty123!",
                    "welcome1!");

    private StaffPasswordPolicy() {}

    public static int minLength() {
        return MIN_LENGTH;
    }

    public static String specialCharacters() {
        return SPECIAL_CHARS;
    }

    public static List<String> ruleDescriptions() {
        return List.of(
                "Be at least " + MIN_LENGTH + " characters long",
                "Include at least one uppercase letter (A-Z)",
                "Include at least one lowercase letter (a-z)",
                "Include at least one number (0-9)",
                "Include at least one special character (" + SPECIAL_CHARS + ")",
                "Not contain your username or email address",
                "Not be a commonly used password");
    }

    public static String validate(String password, String email, String name) {
        List<String> failures = evaluateFailures(password, email, name);
        if (!failures.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, failures.get(0));
        }
        return password;
    }

    static List<String> evaluateFailures(String password, String email, String name) {
        List<String> failures = new ArrayList<>();
        if (password == null || password.length() < MIN_LENGTH) {
            failures.add("Password must be at least 10 characters long");
        }
        if (password == null || !UPPER.matcher(password).find()) {
            failures.add("Password must include at least one uppercase letter (A-Z)");
        }
        if (password == null || !LOWER.matcher(password).find()) {
            failures.add("Password must include at least one lowercase letter (a-z)");
        }
        if (password == null || !DIGIT.matcher(password).find()) {
            failures.add("Password must include at least one number (0-9)");
        }
        if (password == null || !SPECIAL.matcher(password).find()) {
            failures.add("Password must include at least one special character (" + SPECIAL_CHARS + ")");
        }
        if (password != null && containsIdentity(password, email, name)) {
            failures.add("Password must not contain your username or email address");
        }
        if (password != null && isCommonPassword(password)) {
            failures.add("Password must not be a commonly used password");
        }
        return failures;
    }

    private static boolean containsIdentity(String password, String email, String name) {
        String pwd = password.toLowerCase(Locale.ROOT);
        if (pwd.isEmpty()) {
            return false;
        }
        for (String token : identityTokens(email, name)) {
            if (pwd.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> identityTokens(String email, String name) {
        Set<String> tokens = new LinkedHashSet<>();
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!normalizedEmail.isEmpty()) {
            tokens.add(normalizedEmail);
            int at = normalizedEmail.indexOf('@');
            if (at > 0) {
                String local = normalizedEmail.substring(0, at);
                if (local.length() >= 3) {
                    tokens.add(local);
                }
            }
        }
        String normalizedName = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (normalizedName.length() >= 3) {
            tokens.add(normalizedName);
            for (String part : normalizedName.split("\\s+")) {
                if (part.length() >= 3) {
                    tokens.add(part);
                }
            }
        }
        return tokens;
    }

    private static boolean isCommonPassword(String password) {
        String normalized = password.trim().toLowerCase(Locale.ROOT);
        return !normalized.isEmpty() && COMMON_PASSWORDS.contains(normalized);
    }
}
