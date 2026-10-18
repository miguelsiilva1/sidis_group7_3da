package aisafe.routes.application.dtos;

/**
 * Fuel an aircraft needs for a route (US227).
 */
public record FuelEfficiencyResponse(
        String registrationNumber,
        Double fuelConsumptionPerDistanceUnit,
        String routeOrigin,
        String routeDestination,
        Double fuelNeededForRoute
) {}
