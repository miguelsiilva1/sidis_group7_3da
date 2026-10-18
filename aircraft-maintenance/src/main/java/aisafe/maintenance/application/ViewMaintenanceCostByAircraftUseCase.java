package aisafe.maintenance.application;

import aisafe.shared.application.Versioned;
import aisafe.maintenance.domain.MaintenanceRecord;
import aisafe.aircrafts.application.AircraftAcrossReplicas;
import aisafe.aircrafts.domain.AircraftNotFoundException;
import aisafe.aircrafts.domain.RegistrationNumber;
import aisafe.maintenance.application.dtos.MaintenanceCostByAircraftResponse;
import aisafe.shared.application.UseCase;

import java.math.BigDecimal;

@UseCase(readOnly = true)
public class ViewMaintenanceCostByAircraftUseCase {

    private final MaintenanceRecordsAcrossReplicas records;
    private final AircraftAcrossReplicas aircraft;

    public ViewMaintenanceCostByAircraftUseCase(MaintenanceRecordsAcrossReplicas records,
                                                AircraftAcrossReplicas aircraft) {
        this.records = records;
        this.aircraft = aircraft;
    }

    /** Sum of the costs of the aircraft's records, from every replica. */
    public MaintenanceCostByAircraftResponse execute(String registrationNumber) {
        RegistrationNumber reg = new RegistrationNumber(registrationNumber);
        if (aircraft.findAll().stream().noneMatch(a -> a.getRegistrationNumber().equals(reg))) {
            throw new AircraftNotFoundException("Aircraft '" + registrationNumber + "' not found.");
        }
        BigDecimal total = records.findAll().stream()
                .map(Versioned::value)
                .filter(r -> r.getAircraftRegistration().equals(reg))
                .map(MaintenanceRecord::getCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new MaintenanceCostByAircraftResponse(registrationNumber, total);
    }
}
