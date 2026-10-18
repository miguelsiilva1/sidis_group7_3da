package aisafe.aircrafts.application;

import aisafe.shared.application.PeerQueries;
import aisafe.shared.application.UseCase;
import aisafe.aircrafts.application.dtos.AircraftForServicesResponse;
import aisafe.aircrafts.application.dtos.RegisterAircraftRequest;
import aisafe.aircrafts.application.dtos.AircraftResponse;
import aisafe.aircrafts.domain.*;
import aisafe.shared.domain.DuplicateResourceException;

/**
 * Use case for registering a new aircraft in the system.
 * Validates input data, checks for duplicates, and saves the new aircraft to the repository.
 * Returns a DTO with the details of the newly registered aircraft.
 */
@UseCase
public class RegisterAircraftUseCase {

    private final AircraftRepository aircraftRepository;
    private final AircraftModelRepository modelRepository;
    private final PeerQueries peerQueries;

    public RegisterAircraftUseCase(AircraftRepository aircraftRepository, AircraftModelRepository modelRepository,
                                   PeerQueries peerQueries) {
        this.aircraftRepository = aircraftRepository;
        this.modelRepository = modelRepository;
        this.peerQueries = peerQueries;
    }

    /**
     * Registers a new aircraft based on the provided request.
     * Validates that the registration number is unique, the model exists, and that the status and seat capacity are valid.
     * Then creates and saves the new aircraft.
     * @param request the details of the aircraft to register
     * @return a DTO containing the details of the newly registered aircraft
     */
    public AircraftResponse execute(RegisterAircraftRequest request) {
        AircraftModel model = modelRepository.findByModelName(request.modelName())
                .orElseThrow(() -> new AircraftInvalidFieldException("Invalid Model Name: " + request.modelName()));

        RegistrationNumber regNum = new RegistrationNumber(request.registrationNumber());

        // Aircrafts are partitioned between replicas: the registration must not exist on any of them
        // (best effort: during a network partition a duplicate can slip through, plan §4.4).
        if (aircraftRepository.existsByRegistrationNumber(regNum)
                || peerQueries.fetchFirst("/internal/aircrafts/" + regNum.getNumber(), AircraftForServicesResponse.class).isPresent()) {
            throw new DuplicateResourceException("Registration number already exists");
        }
        if (!AircraftStatus.isValid(request.status())) {
            throw new AircraftInvalidFieldException("Invalid status value: " + request.status());
        }
        if (request.seatCapacity() > model.getMaximumSeatingCapacity()) {
            throw new AircraftInvalidFieldException("Seat capacity cannot exceed model's maximum seating capacity");
        }
        Aircraft aircraft = new Aircraft(
                AircraftStatus.valueOf(request.status().toUpperCase()),
                request.manufacturingDate(),
                model,
                regNum,
                request.seatCapacity(),
                request.range(),
                request.features()
        );
        aircraftRepository.save(aircraft);

        Long version = aircraftRepository.findVersionFor(regNum);
        return AircraftResponse.from(aircraft, version);
    }
}