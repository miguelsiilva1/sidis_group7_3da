package aisafe.fleet.infrastructure;

import aisafe.fleet.domain.AircraftModelNotFoundException;
import aisafe.fleet.domain.AircraftNotFoundException;
import aisafe.fleet.domain.FleetUnavailableException;
import aisafe.shared.infrastructure.GlobalExceptionHandler.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Answers of aircraft-maintenance as seen by the client: an aircraft or model that does not exist
 * is a 404 (as in the monolith), and no answer from aircraft-maintenance is a 503 with
 * {@code Retry-After}, never a 404 for something that may exist.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class FleetExceptionHandler {

    @ExceptionHandler({AircraftNotFoundException.class, AircraftModelNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(FleetUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleUnavailable(FleetUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(new ErrorResponse(ex.getMessage()));
    }
}
