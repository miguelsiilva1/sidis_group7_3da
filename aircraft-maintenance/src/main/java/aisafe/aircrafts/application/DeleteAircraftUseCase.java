package aisafe.aircrafts.application;

import aisafe.flightcopies.application.FlightCopiesAcrossReplicas;
import aisafe.maintenance.application.MaintenanceRecordsAcrossReplicas;
import aisafe.shared.application.UseCase;
import aisafe.aircrafts.domain.Aircraft;
import aisafe.aircrafts.domain.AircraftNotFoundException;
import aisafe.aircrafts.domain.AircraftRepository;
import aisafe.aircrafts.domain.RegistrationNumber;
import aisafe.shared.domain.ResourceInUseException;

/**
 * Use case for deleting an existing aircraft from the system.
 */
@UseCase
public class DeleteAircraftUseCase {
    private final AircraftRepository aircraftRepository;
    private final MaintenanceRecordsAcrossReplicas records;
    private final FlightCopiesAcrossReplicas flights;

    public DeleteAircraftUseCase(AircraftRepository aircraftRepository, 
                                 MaintenanceRecordsAcrossReplicas records,
                                 FlightCopiesAcrossReplicas flights) {
        this.aircraftRepository = aircraftRepository;
        this.records = records;
        this.flights = flights;
    }

    /**
     * Deletes the aircraft with the specified registration number from the system.
     * @param registration The registration number of the aircraft to delete.
     */
    public void execute(RegistrationNumber registration) {
        Aircraft aircraft = aircraftRepository.findByRegistrationNumber(registration)
                .orElseThrow(() -> new AircraftNotFoundException("Aircraft not found with registration: " + registration.getNumber()));

        // Flight copies and records are partitioned: look on every replica
        if (flights.findAll().stream().anyMatch(f -> f.getAircraftRegistrationNumber().equals(registration))) {
            throw new ResourceInUseException("Cannot delete aircraft because it is assigned to scheduled flights.");
        }

        if (records.findAll().stream().anyMatch(v -> v.value().getAircraftRegistration().equals(registration))) {
            throw new ResourceInUseException("Cannot delete aircraft because it has maintenance records.");
        }

        aircraftRepository.delete(aircraft);
    }
}
