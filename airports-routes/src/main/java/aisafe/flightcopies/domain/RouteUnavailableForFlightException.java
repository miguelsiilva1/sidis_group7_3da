package aisafe.flightcopies.domain;

import aisafe.shared.domain.ResourceInUseException;

/**
 * The route of a newly scheduled flight is no longer active. Rejecting the copy makes flight-ops
 * undo the scheduling (Saga compensation).
 */
public class RouteUnavailableForFlightException extends ResourceInUseException {
    public RouteUnavailableForFlightException(String origin, String destination) {
        super("Route " + origin + "-" + destination + " is not active for a new flight.");
    }
}
