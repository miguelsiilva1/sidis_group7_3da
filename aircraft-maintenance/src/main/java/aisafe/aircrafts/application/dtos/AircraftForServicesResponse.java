package aisafe.aircrafts.application.dtos;

import aisafe.aircrafts.domain.Aircraft;
import aisafe.aircrafts.domain.AircraftStatus;

/**
 * What other services need to know about an aircraft (contract C1): flight-ops checks status,
 * capacity and range when scheduling, and airports-routes uses range, capacity and fuel
 * consumption for compatible routes (US203) and fuel efficiency (US227).
 */
public record AircraftForServicesResponse(
        String registrationNumber,
        String model,
        AircraftStatus status,
        Integer seatCapacity,
        Double range,
        Double fuelConsumptionPerDistanceUnit
) {
    public static AircraftForServicesResponse from(Aircraft aircraft) {
        return new AircraftForServicesResponse(
                aircraft.getRegistrationNumber().getNumber(),
                aircraft.getModel().getModelName(),
                aircraft.getStatus(),
                aircraft.getSeatCapacity(),
                aircraft.getRange(),
                aircraft.getFuelConsumptionPerDistanceUnit()
        );
    }
}
