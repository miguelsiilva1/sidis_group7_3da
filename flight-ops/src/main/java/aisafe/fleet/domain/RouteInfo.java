package aisafe.fleet.domain;

/**
 * What this service knows about a route of airports-routes (contract C2): flight-ops checks
 * status, minimum capacity and minimum range when scheduling, and gets the distance already calculated.
 */
public record RouteInfo(
        String originIataCode,
        String destinationIataCode,
        String status,
        Integer estimatedFlightTime,
        Integer minimumCapacity,
        Double minimumRange,
        Double distanceKm
) {
}
