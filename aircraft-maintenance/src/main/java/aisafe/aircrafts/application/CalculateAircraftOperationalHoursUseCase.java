package aisafe.aircrafts.application;

import aisafe.flightcopies.domain.FlightCopyStatus;
import aisafe.flightcopies.application.FlightCopiesAcrossReplicas;
import aisafe.aircrafts.application.dtos.CalculateAircraftOperationalHoursRequest;
import aisafe.aircrafts.application.dtos.AircraftOperationalHoursResponse;
import aisafe.aircrafts.domain.AircraftNotFoundException;
import aisafe.aircrafts.domain.RegistrationNumber;
import aisafe.shared.application.UseCase;

/**
 * Calculates the total operational hours for a specific aircraft in the fleet.
 */
@UseCase(readOnly = true)
public class CalculateAircraftOperationalHoursUseCase {

    private final FlightCopiesAcrossReplicas flights;
    private final AircraftAcrossReplicas aircraft;

    public CalculateAircraftOperationalHoursUseCase(FlightCopiesAcrossReplicas flights,
                                                    AircraftAcrossReplicas aircraft) {
        this.flights = flights;
        this.aircraft = aircraft;
    }

    /** Sum of the durations of the aircraft's completed flights, from every replica. */
    public AircraftOperationalHoursResponse execute(CalculateAircraftOperationalHoursRequest request) {
        RegistrationNumber registrationNumber = request.registrationNumber();
        if (aircraft.findAll().stream().noneMatch(a -> a.getRegistrationNumber().equals(registrationNumber))) {
            throw new AircraftNotFoundException("Aircraft with registration " + registrationNumber.getNumber() + " not found.");
        }

        long seconds = flights.findAll().stream()
                .filter(f -> f.getAircraftRegistrationNumber().equals(registrationNumber))
                .filter(f -> f.getStatus() == FlightCopyStatus.COMPLETED)
                .mapToLong(f -> f.getDuration().toSeconds())
                .sum();

        return new AircraftOperationalHoursResponse(registrationNumber.getNumber(), seconds / 3600.0);
    }
}
