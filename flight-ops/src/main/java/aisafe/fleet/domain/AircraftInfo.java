package aisafe.fleet.domain;

/**
 * What this service knows about an aircraft of aircraft-maintenance (contract C1): enough to
 * verify availability and compatibility with routes.
 */
public record AircraftInfo(
        String registrationNumber,
        String model,
        String status,
        Integer seatCapacity,
        Double range,
        Double fuelConsumptionPerDistanceUnit
) {
}
