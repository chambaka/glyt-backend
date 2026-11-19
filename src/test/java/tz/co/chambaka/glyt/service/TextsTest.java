package tz.co.chambaka.glyt.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import tz.co.chambaka.glyt.domain.enums.BagStatus;
import tz.co.chambaka.glyt.domain.enums.Role;
import tz.co.chambaka.glyt.web.ApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextsTest {

    @Test
    void requiredTrimsValue() {
        assertThat(Texts.required("  hello  ", "Name")).isEqualTo("hello");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n  "})
    void requiredRejectsBlank(String value) {
        assertThatThrownBy(() -> Texts.required(value, "Name"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("Name is required.");
                });
    }

    @Test
    void optionalReturnsNullForBlank() {
        assertThat(Texts.optional(null)).isNull();
        assertThat(Texts.optional("")).isNull();
        assertThat(Texts.optional("   ")).isNull();
    }

    @Test
    void optionalTrimsValue() {
        assertThat(Texts.optional("  x ")).isEqualTo("x");
    }

    @Test
    void dateAcceptsIsoDate() {
        assertThat(Texts.date(" 2026-03-01 ", "Date")).isEqualTo("2026-03-01");
    }

    @ParameterizedTest
    @ValueSource(strings = {"01/03/2026", "2026-13-01", "2026-02-30", "tomorrow"})
    void dateRejectsInvalid(String value) {
        assertThatThrownBy(() -> Texts.date(value, "Travel date"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("Travel date must be a date (YYYY-MM-DD).");
                });
    }

    @Test
    void dateRejectsBlankWithRequiredMessage() {
        assertThatThrownBy(() -> Texts.date(" ", "Travel date"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Travel date is required.");
    }

    @Test
    void passwordAcceptsStrongPassword() {
        assertThat(Texts.password("Strong#Pass99")).isEqualTo("Strong#Pass99");
    }

    @Test
    void passwordRejectsBlank() {
        assertThatThrownBy(() -> Texts.password(" "))
                .isInstanceOf(ApiException.class)
                .hasMessage("Password is required.");
    }

    @Test
    void passwordRejectsWeak() {
        assertThatThrownBy(() -> Texts.password("short"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("at least 10 characters");
    }

    @Test
    void passwordRejectsIdentityMatch() {
        assertThatThrownBy(() -> Texts.password("Johnny#Pass99", "johnny@example.com", "Someone Else"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("username or email");
    }

    @Test
    void passwordAcceptsWhenIdentityNotContained() {
        assertThat(Texts.password("Zebra#Pass99!", "johnny@example.com", "Amina Hassan"))
                .isEqualTo("Zebra#Pass99!");
    }

    @Test
    @DisplayName("maskPassport hides all but last 4 characters")
    void maskPassportMasksAllButLastFour() {
        assertThat(Texts.maskPassport("AB1234567")).isEqualTo("•••••4567");
        assertThat(Texts.maskPassport("  AB1234567 ")).isEqualTo("•••••4567");
    }

    @Test
    void maskPassportShortValuesFullyMasked() {
        assertThat(Texts.maskPassport("1234")).isEqualTo("••••");
        assertThat(Texts.maskPassport("ab")).isEqualTo("••••");
    }

    @Test
    void maskPassportBlankReturnsEmpty() {
        assertThat(Texts.maskPassport(null)).isEmpty();
        assertThat(Texts.maskPassport("   ")).isEmpty();
    }

    @Test
    void parseNormalisesCaseSpacesAndDashes() {
        assertThat(Texts.parse(BagStatus.class, "received kia", "Status")).isEqualTo(BagStatus.RECEIVED_KIA);
        assertThat(Texts.parse(BagStatus.class, " handed-airline ", "Status")).isEqualTo(BagStatus.HANDED_AIRLINE);
        assertThat(Texts.parse(Role.class, "admin", "Role")).isEqualTo(Role.ADMIN);
    }

    @Test
    void parseRejectsUnknownValue() {
        assertThatThrownBy(() -> Texts.parse(Role.class, "wizard", "Role"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("Role is not recognised.");
                });
    }

    @Test
    void parseRejectsBlank() {
        assertThatThrownBy(() -> Texts.parse(Role.class, null, "Role"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Role is required.");
    }
}
