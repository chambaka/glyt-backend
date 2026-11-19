package tz.co.chambaka.glyt.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionTest {

    @Test
    void carriesStatusAndMessage() {
        ApiException ex = new ApiException(HttpStatus.CONFLICT, "Already exists.");

        assertThat(ex.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getMessage()).isEqualTo("Already exists.");
    }

    @Test
    void isUncheckedException() {
        assertThat(new ApiException(HttpStatus.BAD_REQUEST, "x")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void hasNoCause() {
        assertThat(new ApiException(HttpStatus.NOT_FOUND, "x").getCause()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = {"BAD_REQUEST", "UNAUTHORIZED", "FORBIDDEN", "NOT_FOUND", "INTERNAL_SERVER_ERROR"})
    void keepsEachStatus(HttpStatus status) {
        assertThat(new ApiException(status, "m").status()).isEqualTo(status);
    }

    @Test
    void allowsNullMessage() {
        assertThat(new ApiException(HttpStatus.BAD_REQUEST, null).getMessage()).isNull();
    }
}
