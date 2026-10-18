package aisafe.fleet.domain;

/**
 * What this service knows about an aircraft of aircraft-maintenance (contract C1): enough to
 * find its compatible routes (US203) and its fuel needs (US227).
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
