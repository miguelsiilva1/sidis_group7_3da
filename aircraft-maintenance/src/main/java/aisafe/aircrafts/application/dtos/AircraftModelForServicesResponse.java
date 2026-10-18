package aisafe.aircrafts.application.dtos;

import aisafe.aircrafts.domain.AircraftModel;
import aisafe.aircrafts.domain.Manufacturer;

/**
 * What other services need to know about an aircraft model (contract C3): airports-routes only
 * certifies an airport for models that exist (US106a).
 */
public record AircraftModelForServicesResponse(String modelName, Manufacturer manufacturer) {

    public static AircraftModelForServicesResponse from(AircraftModel model) {
        return new AircraftModelForServicesResponse(model.getModelName(), model.getManufacturer());
    }
}
