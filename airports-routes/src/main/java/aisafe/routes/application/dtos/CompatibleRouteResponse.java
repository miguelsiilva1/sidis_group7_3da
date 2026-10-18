package aisafe.routes.application.dtos;

/**
 * A route an aircraft can fly: its range and seat capacity reach the route's minimums (US203).
 */
public record CompatibleRouteResponse(String originIataCode, String destinationIataCode, Integer estimatedFlightTime, Double minimumRange, Integer minimumCapacity) {}
