package aisafe.aircrafts.application;

import aisafe.shared.application.UseCase;
import aisafe.aircrafts.domain.AircraftModel;
import aisafe.aircrafts.domain.AircraftModelNotFoundException;
import aisafe.aircrafts.domain.AircraftModelRepository;
import aisafe.shared.domain.ResourceInUseException;

/**
 * Use case for deleting an existing aircraft model from the system.
 */
@UseCase
public class DeleteAircraftModelUseCase {
    private final AircraftModelRepository aircraftModelRepository;
    private final AircraftAcrossReplicas aircraft;

    public DeleteAircraftModelUseCase(AircraftModelRepository aircraftModelRepository, AircraftAcrossReplicas aircraft) {
        this.aircraftModelRepository = aircraftModelRepository;
        this.aircraft = aircraft;
    }

    /**
     * Deletes the aircraft model with the specified name from the system.
     * @param modelName The name of the aircraft model to delete.
     */
    public void execute(String modelName) {
        AircraftModel model = aircraftModelRepository.findByModelName(modelName)
                .orElseThrow(() -> new AircraftModelNotFoundException("Aircraft model not found with name " + modelName));

        // Aircraft are partitioned: an aircraft of another replica may use the model
        if (aircraft.findAll().stream().anyMatch(a -> a.getModel().getModelName().equals(modelName))) {
            throw new ResourceInUseException("Cannot delete aircraft model because there are aircraft associated with it.");
        }

        aircraftModelRepository.delete(model);
    }
}
