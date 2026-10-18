package aisafe.aircrafts.domain;

import aisafe.shared.domain.NotFoundException;

/**
 * Raised when the requested aircraft model cannot be found.
 */
public class AircraftModelNotFoundException extends NotFoundException {
    public AircraftModelNotFoundException(String message) {
        super(message);
    }
}
