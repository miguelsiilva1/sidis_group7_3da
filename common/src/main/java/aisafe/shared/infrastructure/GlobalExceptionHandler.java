package aisafe.shared.infrastructure;

import aisafe.shared.domain.ConcurrencyException;
import aisafe.shared.domain.DomainException;
import aisafe.shared.domain.DuplicateResourceException;
import aisafe.shared.domain.NotFoundException;
import aisafe.shared.domain.ResourceInUseException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralised exception handler shared by every service. It translates domain and
 * infrastructure exceptions into meaningful HTTP responses.
 *
 * <p>It knows no service's domain: not-found exceptions extend {@link NotFoundException}
 * and every other domain rule violation extends {@link DomainException}. A service that
 * needs a different status for one of its exceptions adds its own
 * {@code @RestControllerAdvice} with a higher precedence.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 404 Not Found */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(ex.getMessage()));
    }

    /** 404 Not Found - no route for this path (the monolith answered 500) */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoRoute(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("No route for " + ex.getHttpMethod() + " " + ex.getResourcePath()));
    }

    /** 400 Bad Request - Invalid args.*/
    @ExceptionHandler({
            DomainException.class,
            IllegalArgumentException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(ex.getMessage()));
    }

    /** 409 Conflict - duplicated information or resource in use */
    @ExceptionHandler({DataIntegrityViolationException.class, DuplicateResourceException.class, ResourceInUseException.class})
    public ResponseEntity<ErrorResponse> handleConflict(RuntimeException ex) {
        String msg = (ex instanceof DuplicateResourceException || ex instanceof ResourceInUseException)
                ? ex.getMessage()
                : "A resource with the given unique identifier already exists.";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(msg));
    }

    /** 403 Forbidden - Token is valid, but user doesnt have needed role */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("You do not have permission to access this resource."));
    }

    /** 409 Conflict - optimistic locking collision (concurrent update detected) */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("The resource was modified by another request. Please retry."));
    }

    /** 412 Precondition Failed - optimistic locking collision (If-Match mismatch) */
    @ExceptionHandler(ConcurrencyException.class)
    public ResponseEntity<ErrorResponse> handlePreconditionFailed(ConcurrencyException ex) {
        return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                .body(new ErrorResponse(ex.getMessage()));
    }

    /** 400 Bad Request - query/path parameter cannot be converted to its target type (e.g. invalid enum value) */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'.";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(message));
    }

    /** 400 Bad Request - bean validation failure (@Valid on request bodies) */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage, (a, b) -> a));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ValidationErrorResponse("Validation failed", errors));
    }

    /** 500 Internal Server Error - Catch all for unexpected error */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllOtherExceptions(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("An unexpected internal server error occurred: " + ex.getMessage()));
    }

    /** Error response body. */
    public record ErrorResponse(String message) {}

    /** Error response body for validation failures -- includes per-field details. */
    public record ValidationErrorResponse(String message, Map<String, String> errors) {}
}
