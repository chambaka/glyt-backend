package tz.co.chambaka.glyt.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import tz.co.chambaka.glyt.web.ApiException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StaffPasswordPolicyTest {

    private static final String GOOD = "Zebra#Pass99";

    @Test
    void exposesMetadata() {
        assertThat(StaffPasswordPolicy.minLength()).isEqualTo(10);
        assertThat(StaffPasswordPolicy.specialCharacters()).isEqualTo("!@#$%^&*");
        assertThat(StaffPasswordPolicy.SPECIAL_CHARS).isEqualTo("!@#$%^&*");
    }

    @Test
    void ruleDescriptionsListAllRules() {
        List<String> rules = StaffPasswordPolicy.ruleDescriptions();
        assertThat(rules).hasSize(7);
        assertThat(rules.get(0)).contains("10");
        assertThat(rules.get(4)).contains("!@#$%^&*");
    }

    @Test
    void validReturnsPasswordUnchanged() {
        assertThat(StaffPasswordPolicy.validate(GOOD, null, null)).isEqualTo(GOOD);
        assertThat(StaffPasswordPolicy.evaluateFailures(GOOD, "a@b.co", "Someone")).isEmpty();
    }

    @Test
    void nullPasswordFailsEveryStructuralRule() {
        List<String> failures = StaffPasswordPolicy.evaluateFailures(null, null, null);
        assertThat(failures).hasSize(5);
        assertThat(failures.get(0)).contains("10 characters");
    }

    @Test
    void tooShort() {
        assertThat(StaffPasswordPolicy.evaluateFailures("Ab#1xyz", null, null))
                .containsExactly("Password must be at least 10 characters long");
    }

    @Test
    void missingUppercase() {
        assertThat(StaffPasswordPolicy.evaluateFailures("zebra#pass99", null, null))
                .containsExactly("Password must include at least one uppercase letter (A-Z)");
    }

    @Test
    void missingLowercase() {
        assertThat(StaffPasswordPolicy.evaluateFailures("ZEBRA#PASS99", null, null))
                .containsExactly("Password must include at least one lowercase letter (a-z)");
    }

    @Test
    void missingDigit() {
        assertThat(StaffPasswordPolicy.evaluateFailures("Zebra#PassXX", null, null))
                .containsExactly("Password must include at least one number (0-9)");
    }

    @Test
    void missingSpecial() {
        assertThat(StaffPasswordPolicy.evaluateFailures("ZebraPass9999", null, null))
                .containsExactly("Password must include at least one special character (!@#$%^&*)");
    }

    @Test
    void specialOutsideAllowedSetDoesNotCount() {
        assertThat(StaffPasswordPolicy.evaluateFailures("ZebraPass99-_", null, null))
                .hasSize(1)
                .first().asString().contains("special character");
    }

    @Test
    void emailMustNotBeContained() {
        List<String> failures = StaffPasswordPolicy.evaluateFailures("xxjohn@example.com#A1", "john@example.com", null);
        assertThat(failures).contains("Password must not contain your username or email address");
    }

    @Test
    void emailLocalPartMustNotBeContained() {
        List<String> failures = StaffPasswordPolicy.evaluateFailures("Johnny#Pass99", "johnny@example.com", null);
        assertThat(failures).containsExactly("Password must not contain your username or email address");
    }

    @Test
    void shortEmailLocalPartIgnored() {
        // local part "ab" is < 3 chars so only the full email is a token
        assertThat(StaffPasswordPolicy.evaluateFailures("Zab#Pass9900", "ab@example.com", null)).isEmpty();
    }

    @Test
    void emailWithoutAtSignUsedAsWholeToken() {
        assertThat(StaffPasswordPolicy.evaluateFailures("Xusername#A12", "username", null))
                .containsExactly("Password must not contain your username or email address");
    }

    @Test
    void emailStartingWithAtHasNoLocalToken() {
        assertThat(StaffPasswordPolicy.evaluateFailures(GOOD, "@example.com", null)).isEmpty();
    }

    @Test
    void nameAndNamePartsMustNotBeContained() {
        assertThat(StaffPasswordPolicy.evaluateFailures("Hassan#Pass99", null, "Amina Hassan"))
                .containsExactly("Password must not contain your username or email address");
        assertThat(StaffPasswordPolicy.evaluateFailures("Amina#Pass99x", null, "Amina Hassan"))
                .hasSize(1);
        assertThat(StaffPasswordPolicy.evaluateFailures("Zebra#Pass99", null, "Amina Hassan")).isEmpty();
    }

    @Test
    void shortNameIgnored() {
        assertThat(StaffPasswordPolicy.evaluateFailures("Zebra#Pass99", null, "Zb")).isEmpty();
    }

    @Test
    void blankEmailAndNameIgnored() {
        assertThat(StaffPasswordPolicy.evaluateFailures(GOOD, "   ", "   ")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Password123!", "Qwerty123!", "Welcome1!", "PASSWORD123!"})
    void commonPasswordsRejected(String password) {
        assertThat(StaffPasswordPolicy.evaluateFailures(password, null, null))
                .contains("Password must not be a commonly used password");
    }

    @Test
    void commonPasswordCheckTrimsAndIgnoresCase() {
        assertThat(StaffPasswordPolicy.evaluateFailures("  Password123!  ", null, null))
                .contains("Password must not be a commonly used password");
    }

    @Test
    void emptyPasswordHasNoIdentityFailure() {
        List<String> failures = StaffPasswordPolicy.evaluateFailures("", "john@example.com", null);
        assertThat(failures).doesNotContain("Password must not contain your username or email address");
        assertThat(failures).isNotEmpty();
    }

    @Test
    void validateThrowsFirstFailureAsBadRequest() {
        assertThatThrownBy(() -> StaffPasswordPolicy.validate("short", null, null))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("Password must be at least 10 characters long");
                });
    }

    @Test
    void validateThrowsForNull() {
        assertThatThrownBy(() -> StaffPasswordPolicy.validate(null, null, null))
                .isInstanceOf(ApiException.class);
    }
}
