package aisafe.flights.domain;

import aisafe.shared.domain.DomainException;

/**
 * Raised when a provided registration number is null, empty, or does not match the required format.
 */
public class InvalidRegistrationNumberException extends DomainException {
    public InvalidRegistrationNumberException(String message) {
        super(message);
    }
}
