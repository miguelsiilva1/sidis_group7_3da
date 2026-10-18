package aisafe.routes.application.dtos;

import aisafe.routes.domain.Route;
import aisafe.routes.domain.RouteStatus;

/**
 * What other services need to know about a route (contract C2): flight-ops checks status,
 * minimum capacity and minimum range when scheduling, and gets the distance already calculated.
 */
public record RouteForServicesResponse(
        String originIataCode,
        String destinationIataCode,
        RouteStatus status,
        Integer estimatedFlightTime,
        Integer minimumCapacity,
        Double minimumRange,
        Double distanceKm
) {
    public static RouteForServicesResponse from(Route route, double distanceKm) {
        return new RouteForServicesResponse(
                route.getOrigin().getCode(),
                route.getDestination().getCode(),
                route.getStatus(),
                route.getEstimatedFlightTime(),
                route.getMinimumCapacity(),
                route.getMinimumRange(),
                distanceKm
        );
    }
}
