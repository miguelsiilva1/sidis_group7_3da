package aisafe.fleet.domain;

import aisafe.shared.domain.DomainException;

/**
 * aircraft-maintenance did not answer, so whether the aircraft or model exists is unknown.
 */
public class FleetUnavailableException extends DomainException {
    public FleetUnavailableException(String message) {
        super(message);
    }
}
