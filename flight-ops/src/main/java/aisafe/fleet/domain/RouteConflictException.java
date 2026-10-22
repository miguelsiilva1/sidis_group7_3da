package aisafe.fleet.domain;

/**
 * airports-routes rejected a flight copy (409) because the route is inactive or unavailable.
 */
public class RouteConflictException extends FleetConflictException {
    public RouteConflictException(String message) {
        super(message);
    }
}
