package org.enoria.mockbrevo.brevo;

import java.util.Map;
import org.springframework.http.ResponseEntity;

/** Brevo's error body: {@code {"code": "...", "message": "..."}}. */
final class BrevoErrors {

    private BrevoErrors() {}

    static ResponseEntity<Object> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("code", "invalid_parameter", "message", message));
    }

    /** Thrown by parameter checks; the controller turns it into {@link #badRequest}. */
    static final class InvalidParameter extends RuntimeException {
        private static final long serialVersionUID = 1L;

        InvalidParameter(String message) {
            super(message);
        }
    }
}
