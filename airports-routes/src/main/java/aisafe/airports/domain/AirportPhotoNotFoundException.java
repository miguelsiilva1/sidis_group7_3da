package aisafe.airports.domain;

import aisafe.shared.domain.NotFoundException;

public class AirportPhotoNotFoundException extends NotFoundException {
    public AirportPhotoNotFoundException(String iataCode) {
        super("Airport '" + iataCode + "' has no photo.");
    }
}
