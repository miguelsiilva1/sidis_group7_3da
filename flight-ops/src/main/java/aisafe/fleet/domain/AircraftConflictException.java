package aisafe.fleet.domain;

/**
 * aircraft-maintenance rejected a flight copy (409) because the aircraft is unavailable or inactive.
 */
public class AircraftConflictException extends FleetConflictException {
    public AircraftConflictException(String message) {
        super(message);
    }
}
