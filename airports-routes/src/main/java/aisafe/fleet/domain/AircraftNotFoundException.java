package aisafe.fleet.domain;

import aisafe.shared.domain.DomainException;

/**
 * aircraft-maintenance has no aircraft with this registration. Not a {@code NotFoundException}:
 * the peers of this service would give the same answer, so the request is not forwarded to them.
 */
public class AircraftNotFoundException extends DomainException {
    public AircraftNotFoundException(String registration) {
        super("Aircraft not found with registration: " + registration);
    }
}
