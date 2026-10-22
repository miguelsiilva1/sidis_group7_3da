package aisafe.flights.domain;

import aisafe.shared.domain.DomainException;

/**
 * Raised when an invalid IATA code is provided.
 */
public class InvalidIataCodeException extends DomainException {
    public InvalidIataCodeException(String message) {
        super(message);
    }
}
