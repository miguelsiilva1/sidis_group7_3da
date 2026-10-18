package aisafe.maintenance.application;

import aisafe.shared.application.Versioned;
import aisafe.maintenance.domain.MaintenanceStatus;
import aisafe.aircrafts.domain.RegistrationNumber;
import aisafe.aircrafts.domain.Aircraft;
import aisafe.aircrafts.application.AircraftAcrossReplicas;
import aisafe.aircrafts.domain.AircraftModelNotFoundException;
import aisafe.aircrafts.domain.AircraftModelRepository;
import aisafe.maintenance.application.dtos.AverageTurnaroundByModelResponse;
import aisafe.shared.application.UseCase;

import java.util.stream.Collectors;
import java.util.Set;
import java.time.Duration;

@UseCase(readOnly = true)
public class ViewAverageMaintenanceTurnaroundUseCase {

    private final MaintenanceRecordsAcrossReplicas records;
    private final AircraftAcrossReplicas aircraft;
    private final AircraftModelRepository aircraftModelRepository;

    public ViewAverageMaintenanceTurnaroundUseCase(MaintenanceRecordsAcrossReplicas records,
                                                   AircraftAcrossReplicas aircraft,
                                                   AircraftModelRepository aircraftModelRepository) {
        this.records = records;
        this.aircraft = aircraft;
        this.aircraftModelRepository = aircraftModelRepository;
    }

    /**
     * Average time, in hours, between the start and the completion of the completed records of
     * every aircraft of the model, from every replica (0 when there are none).
     */
    public AverageTurnaroundByModelResponse execute(String modelName) {
        if (!aircraftModelRepository.existsByModelName(modelName)) {
            throw new AircraftModelNotFoundException("Aircraft model '" + modelName + "' not found.");
        }
        Set<RegistrationNumber> registrations = aircraft.findAll().stream()
                .filter(a -> a.getModel().getModelName().equals(modelName))
                .map(Aircraft::getRegistrationNumber)
                .collect(Collectors.toSet());
        double averageSeconds = records.findAll().stream()
                .map(Versioned::value)
                .filter(r -> registrations.contains(r.getAircraftRegistration()))
                .filter(r -> r.getStatus() == MaintenanceStatus.COMPLETED && r.getCompletedAt() != null)
                .mapToLong(r -> Duration.between(r.getStartDate(), r.getCompletedAt()).toSeconds())
                .average()
                .orElse(0.0);
        return new AverageTurnaroundByModelResponse(modelName, averageSeconds / 3600.0);
    }
}
