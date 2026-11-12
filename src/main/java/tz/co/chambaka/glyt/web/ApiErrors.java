package tz.co.chambaka.glyt.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tz.co.chambaka.glyt.logging.CorrelationIds;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiErrors {

    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> api(ApiException ex) {
        log.warn("api error status={} message={}", ex.status().value(), ex.getMessage());
        return ResponseEntity.status(ex.status()).body(errorBody(ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> denied(AccessDeniedException ex) {
        log.warn("access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(errorBody("You do not have permission for this action."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage() == null ? "Check the form and try again." : error.getDefaultMessage())
                .findFirst()
                .orElse("Check the form and try again.");
        log.warn("validation failed: {}", message);
        return ResponseEntity.badRequest().body(errorBody(message));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> tooLarge(MaxUploadSizeExceededException ex) {
        log.warn("upload too large");
        return ResponseEntity.badRequest().body(errorBody("Photo must be under 8 MB."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> missing(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody("Not found."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> other(Exception ex) {
        log.error("request failed", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody("Something went wrong. Try again."));
    }

    private static Map<String, String> errorBody(String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", message);
        String correlationId = MDC.get(CorrelationIds.MDC_CORRELATION_ID);
        if (correlationId != null && !correlationId.isBlank()) {
            body.put("correlationId", correlationId);
        }
        return body;
    }
}
