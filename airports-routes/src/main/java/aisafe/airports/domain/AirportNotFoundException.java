package aisafe.airports.domain;

import aisafe.shared.domain.NotFoundException;

/**
 * Raised when the requested airport cannot be found.
 */
public class AirportNotFoundException extends NotFoundException {
    public AirportNotFoundException(String iataCode) {
        super("Airport with IATA code '" + iataCode + "' not found.");
    }
}
