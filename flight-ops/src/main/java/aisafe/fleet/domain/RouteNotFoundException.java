package aisafe.fleet.domain;

import aisafe.shared.domain.DomainException;

/**
 * airports-routes has no route for this origin and destination. Not a {@code NotFoundException}:
 * the peers of this service would give the same answer, so the request is not forwarded to them.
 */
public class RouteNotFoundException extends DomainException {
    public RouteNotFoundException(String origin, String destination) {
        super("Route not found: " + origin + " -> " + destination);
    }

    public RouteNotFoundException(String message) {
        super(message);
    }
}
