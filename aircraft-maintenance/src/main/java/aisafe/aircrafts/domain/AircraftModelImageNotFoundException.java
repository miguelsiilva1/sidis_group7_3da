package aisafe.aircrafts.domain;

import aisafe.shared.domain.NotFoundException;

public class AircraftModelImageNotFoundException extends NotFoundException {
    public AircraftModelImageNotFoundException(String message) {
        super(message);
    }
}
