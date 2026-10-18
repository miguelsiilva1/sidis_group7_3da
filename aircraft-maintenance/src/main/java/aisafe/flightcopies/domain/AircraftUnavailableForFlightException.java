package aisafe.flightcopies.domain;

import aisafe.shared.domain.ResourceInUseException;

/**
 * The aircraft of a newly scheduled flight cannot take it (inactive or under maintenance).
 * Rejecting the copy makes flight-ops undo the scheduling (Saga compensation).
 */
public class AircraftUnavailableForFlightException extends ResourceInUseException {
    public AircraftUnavailableForFlightException(String registration) {
        super("Aircraft " + registration + " is not available for a new flight.");
    }
}
