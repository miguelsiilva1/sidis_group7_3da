package aisafe.flights.domain;

import aisafe.shared.domain.NotFoundException;

public class FlightNotFoundException extends NotFoundException {
    public FlightNotFoundException(String message) {
        super(message);
    }
}
