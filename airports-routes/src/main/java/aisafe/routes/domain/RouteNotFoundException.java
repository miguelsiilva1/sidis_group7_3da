package aisafe.routes.domain;

import aisafe.shared.domain.NotFoundException;

/**
 * Raised when the requested route cannot be found.
 */
public class RouteNotFoundException extends NotFoundException {
    public RouteNotFoundException(String id) {
        super("Route with ID " + id + " was not found.");
    }
}