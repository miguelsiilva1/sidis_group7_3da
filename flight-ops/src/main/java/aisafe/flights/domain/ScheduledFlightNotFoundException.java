package aisafe.flights.domain;

import aisafe.shared.domain.NotFoundException;

public class ScheduledFlightNotFoundException extends NotFoundException {
    public ScheduledFlightNotFoundException(String message) {
        super(message);
    }
}
