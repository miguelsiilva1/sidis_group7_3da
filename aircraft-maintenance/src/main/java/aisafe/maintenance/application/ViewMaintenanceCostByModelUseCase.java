package aisafe.maintenance.application;

import aisafe.shared.application.Versioned;
import aisafe.maintenance.domain.MaintenanceRecord;
import aisafe.aircrafts.domain.RegistrationNumber;
import aisafe.aircrafts.domain.Aircraft;
import aisafe.aircrafts.application.AircraftAcrossReplicas;
import aisafe.aircrafts.domain.AircraftModelNotFoundException;
import aisafe.aircrafts.domain.AircraftModelRepository;
import aisafe.maintenance.application.dtos.MaintenanceCostByModelResponse;
import aisafe.shared.application.UseCase;

import java.util.stream.Collectors;
import java.util.Set;
import java.math.BigDecimal;

@UseCase(readOnly = true)
public class ViewMaintenanceCostByModelUseCase {

    private final MaintenanceRecordsAcrossReplicas records;
    private final AircraftAcrossReplicas aircraft;
    private final AircraftModelRepository aircraftModelRepository;

    public ViewMaintenanceCostByModelUseCase(MaintenanceRecordsAcrossReplicas records,
                                             AircraftAcrossReplicas aircraft,
                                             AircraftModelRepository aircraftModelRepository) {
        this.records = records;
        this.aircraft = aircraft;
        this.aircraftModelRepository = aircraftModelRepository;
    }

    /** Sum of the costs of the records of every aircraft of the model, from every replica. */
    public MaintenanceCostByModelResponse execute(String modelName) {
        if (!aircraftModelRepository.existsByModelName(modelName)) {
            throw new AircraftModelNotFoundException("Aircraft model '" + modelName + "' not found.");
        }
        Set<RegistrationNumber> registrations = aircraft.findAll().stream()
                .filter(a -> a.getModel().getModelName().equals(modelName))
                .map(Aircraft::getRegistrationNumber)
                .collect(Collectors.toSet());
        if (registrations.isEmpty()) {
            return new MaintenanceCostByModelResponse(modelName, BigDecimal.ZERO);
        }
        BigDecimal total = records.findAll().stream()
                .map(Versioned::value)
                .filter(r -> registrations.contains(r.getAircraftRegistration()))
                .map(MaintenanceRecord::getCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new MaintenanceCostByModelResponse(modelName, total);
    }
}
