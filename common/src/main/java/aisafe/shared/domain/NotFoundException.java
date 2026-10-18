package aisafe.shared.domain;

/**
 * Base class for "resource not found" domain exceptions.
 *
 * <p>Each service's not-found exceptions extend this class, so the shared
 * {@code GlobalExceptionHandler} can map all of them to 404 without knowing
 * any service's domain.</p>
 */
public class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super(message);
    }
}
