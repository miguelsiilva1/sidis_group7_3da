package aisafe.aircrafts.domain;

import aisafe.shared.domain.NotFoundException;

/**
 * Raised when the requested aircraft cannot be found.
 */
public class AircraftNotFoundException extends NotFoundException {
    public AircraftNotFoundException(String message) {
        super(message);
    }
}
