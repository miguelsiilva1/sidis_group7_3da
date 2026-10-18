package aisafe.aircrafts.infrastructure.replication;

import aisafe.aircrafts.domain.Aircraft;
import aisafe.aircrafts.domain.AircraftModel;
import aisafe.aircrafts.domain.AircraftStatus;
import aisafe.aircrafts.domain.RegistrationNumber;

import java.time.LocalDate;
import java.util.List;

/**
 * An aircraft as copied to the other holder (record: held by its home replica and the next one).
 * The model is referenced by name: models are catalogs, present on every replica.
 */
public record AircraftReplica(
        String registrationNumber,
        AircraftStatus status,
        LocalDate manufacturingDate,
        String modelName,
        Integer seatCapacity,
        Double range,
        List<String> features
) {
    public static AircraftReplica from(Aircraft aircraft) {
        return new AircraftReplica(aircraft.getRegistrationNumber().getNumber(), aircraft.getStatus(),
                aircraft.getManufacturingDate(), aircraft.getModel().getModelName(), aircraft.getSeatCapacity(),
                aircraft.getRange(), aircraft.getFeatures());
    }

    public Aircraft toDomain(AircraftModel model) {
        return new Aircraft(status, manufacturingDate, model, new RegistrationNumber(registrationNumber),
                seatCapacity, range, features);
    }
}
