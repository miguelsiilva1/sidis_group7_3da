package aisafe.fleet.domain;

import aisafe.shared.domain.DomainException;

/**
 * aircraft-maintenance has no aircraft model with this name. Not a {@code NotFoundException}:
 * the peers of this service would give the same answer, so the request is not forwarded to them.
 */
public class AircraftModelNotFoundException extends DomainException {
    public AircraftModelNotFoundException(String model) {
        super("Aircraft model not found: " + model);
    }
}
