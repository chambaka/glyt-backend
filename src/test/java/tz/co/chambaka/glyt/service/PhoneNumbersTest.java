package tz.co.chambaka.glyt.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import tz.co.chambaka.glyt.web.ApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNumbersTest {

    @ParameterizedTest
    @CsvSource({
            "0712345678,+255712345678",
            "0612345678,+255612345678",
            "0112345678,+255112345678",
            "712345678,+255712345678",
            "255712345678,+255712345678",
            "+255712345678,+255712345678",
            "'+255 712 345 678',+255712345678",
            "'(0712) 345-678',+255712345678",
            "  0712345678  ,+255712345678"
    })
    void normalizeAcceptsValidFormats(String raw, String expected) {
        assertThat(PhoneNumbers.normalize(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "abc", "12345", "0812345678", "+254712345678", "25571234567", "+2557123456789"})
    void normalizeReturnsNullForInvalid(String raw) {
        assertThat(PhoneNumbers.normalize(raw)).isNull();
    }

    @Test
    void requiredReturnsNormalized() {
        assertThat(PhoneNumbers.required("0712345678")).isEqualTo("+255712345678");
    }

    @Test
    void requiredThrowsBadRequestForInvalid() {
        assertThatThrownBy(() -> PhoneNumbers.required("nope"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).isEqualTo("A valid Tanzania phone number is required.");
                });
    }

    @Test
    void requiredThrowsForNull() {
        assertThatThrownBy(() -> PhoneNumbers.required(null)).isInstanceOf(ApiException.class);
    }

    @Test
    void optionalReturnsNullForBlank() {
        assertThat(PhoneNumbers.optional(null)).isNull();
        assertThat(PhoneNumbers.optional("")).isNull();
        assertThat(PhoneNumbers.optional("   ")).isNull();
    }

    @Test
    void optionalNormalizesWhenPresent() {
        assertThat(PhoneNumbers.optional("0755123456")).isEqualTo("+255755123456");
    }

    @Test
    void optionalThrowsWhenPresentButInvalid() {
        assertThatThrownBy(() -> PhoneNumbers.optional("999"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Tanzania phone number");
    }
}
