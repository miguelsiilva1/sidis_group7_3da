package aisafe.fleet.domain;

import aisafe.shared.domain.DomainException;

/**
 * aircraft-maintenance has no aircraft model with this name. Not forwarded to the peers, as
 * {@link AircraftNotFoundException}.
 */
public class AircraftModelNotFoundException extends DomainException {
    public AircraftModelNotFoundException(String modelName) {
        super("Aircraft model with name '" + modelName + "' not found.");
    }
}
