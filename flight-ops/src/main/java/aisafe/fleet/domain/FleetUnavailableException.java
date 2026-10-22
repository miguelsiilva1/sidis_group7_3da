package aisafe.fleet.domain;

import aisafe.shared.domain.DomainException;

/**
 * An external service (aircraft-maintenance or airports-routes) did not answer or returned 5xx.
 */
public class FleetUnavailableException extends DomainException {
    public FleetUnavailableException(String message) {
        super(message);
    }
}
