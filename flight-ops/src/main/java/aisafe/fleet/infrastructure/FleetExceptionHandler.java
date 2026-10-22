package aisafe.fleet.infrastructure;

import aisafe.fleet.domain.AircraftModelNotFoundException;
import aisafe.fleet.domain.AircraftNotFoundException;
import aisafe.fleet.domain.FleetConflictException;
import aisafe.fleet.domain.FleetUnavailableException;
import aisafe.fleet.domain.RouteNotFoundException;
import aisafe.shared.infrastructure.GlobalExceptionHandler.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps responses and errors from external services (aircraft-maintenance and airports-routes)
 * into HTTP responses:
 * - 404 for resources that do not exist (without triggering replica forwarding)
 * - 409 for conflicts
 * - 503 with Retry-After for timeouts and connection failures
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class FleetExceptionHandler {

    @ExceptionHandler({AircraftNotFoundException.class, AircraftModelNotFoundException.class, RouteNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(FleetConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(FleetConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(FleetUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleUnavailable(FleetUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(new ErrorResponse(ex.getMessage()));
    }
}
