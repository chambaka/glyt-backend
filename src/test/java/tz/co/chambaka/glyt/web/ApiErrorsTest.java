package tz.co.chambaka.glyt.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tz.co.chambaka.glyt.logging.CorrelationIds;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorsTest {

    private final ApiErrors errors = new ApiErrors();

    @BeforeEach
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    // used only to obtain a MethodParameter
    @SuppressWarnings("unused")
    void sample(String body) {}

    private MethodArgumentNotValidException validation(FieldError... fieldErrors) throws Exception {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        for (FieldError error : fieldErrors) {
            binding.addError(error);
        }
        MethodParameter parameter = new MethodParameter(getClass().getDeclaredMethod("sample", String.class), 0);
        return new MethodArgumentNotValidException(parameter, binding);
    }

    @Test
    void apiExceptionMapsToItsStatusAndMessage() {
        ResponseEntity<Map<String, String>> response =
                errors.api(new ApiException(HttpStatus.CONFLICT, "Duplicate booking."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsOnlyKeys("error").containsEntry("error", "Duplicate booking.");
    }

    @Test
    void errorBodyIncludesCorrelationIdWhenPresent() {
        MDC.put(CorrelationIds.MDC_CORRELATION_ID, "corr-12345");

        ResponseEntity<Map<String, String>> response = errors.api(new ApiException(HttpStatus.NOT_FOUND, "Nope."));

        assertThat(response.getBody())
                .containsEntry("error", "Nope.")
                .containsEntry("correlationId", "corr-12345");
    }

    @Test
    void blankCorrelationIdIsOmitted() {
        MDC.put(CorrelationIds.MDC_CORRELATION_ID, "   ");

        ResponseEntity<Map<String, String>> response = errors.api(new ApiException(HttpStatus.NOT_FOUND, "Nope."));

        assertThat(response.getBody()).doesNotContainKey("correlationId");
    }

    @Test
    void accessDeniedMapsToForbiddenWithGenericMessage() {
        ResponseEntity<Map<String, String>> response = errors.denied(new AccessDeniedException("secret detail"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("error", "You do not have permission for this action.");
        assertThat(response.getBody().values()).noneMatch(v -> v.contains("secret detail"));
    }

    @Test
    void validationUsesFirstFieldErrorMessage() throws Exception {
        MethodArgumentNotValidException ex = validation(
                new FieldError("request", "email", "Email is required."),
                new FieldError("request", "name", "Name is required."));

        ResponseEntity<Map<String, String>> response = errors.invalid(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Email is required.");
    }

    @Test
    void validationFallsBackWhenFieldErrorHasNoMessage() throws Exception {
        MethodArgumentNotValidException ex = validation(new FieldError("request", "email", null));

        ResponseEntity<Map<String, String>> response = errors.invalid(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Check the form and try again.");
    }

    @Test
    void validationFallsBackWhenNoFieldErrors() throws Exception {
        ResponseEntity<Map<String, String>> response = errors.invalid(validation());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Check the form and try again.");
    }

    @Test
    void uploadTooLargeIsBadRequest() {
        ResponseEntity<Map<String, String>> response = errors.tooLarge(new MaxUploadSizeExceededException(8_000_000L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Photo must be under 8 MB.");
    }

    @Test
    void missingResourceIsNotFound() {
        ResponseEntity<Map<String, String>> response =
                errors.missing(new NoResourceFoundException(HttpMethod.GET, "nothing/here"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("error", "Not found.");
    }

    @Test
    void unexpectedExceptionIsHiddenBehindGenericServerError() {
        ResponseEntity<Map<String, String>> response = errors.other(new IllegalStateException("db password leaked"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("error", "Something went wrong. Try again.");
        assertThat(response.getBody().values()).noneMatch(v -> v.contains("leaked"));
    }

    @Test
    void unexpectedExceptionIncludesCorrelationId() {
        MDC.put(CorrelationIds.MDC_CORRELATION_ID, "trace-98765");

        ResponseEntity<Map<String, String>> response = errors.other(new RuntimeException("x"));

        assertThat(response.getBody()).containsEntry("correlationId", "trace-98765");
    }

    @Test
    void bodyPreservesKeyOrderErrorFirst() {
        MDC.put(CorrelationIds.MDC_CORRELATION_ID, "order-12345");

        Map<String, String> body = errors.api(new ApiException(HttpStatus.BAD_REQUEST, "m")).getBody();

        assertThat(body.keySet()).containsExactly("error", "correlationId");
    }
}
