package aisafe.fleet.domain;

import aisafe.shared.domain.ResourceInUseException;

/**
 * An external service rejected a flight copy due to a conflict (409).
 */
public class FleetConflictException extends ResourceInUseException {
    public FleetConflictException(String message) {
        super(message);
    }
}
