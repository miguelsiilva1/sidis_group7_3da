package aisafe.aircrafts.application;

import aisafe.aircrafts.application.dtos.AircraftForServicesResponse;
import aisafe.aircrafts.application.dtos.ViewAircraftDetailsRequest;
import aisafe.aircrafts.domain.AircraftNotFoundException;
import aisafe.aircrafts.domain.AircraftRepository;
import aisafe.shared.application.UseCase;

/**
 * Returns what other services need to know about an aircraft held by this replica (contract C1).
 * When this replica does not hold it, the not-found answer makes the request go to the peers.
 */
@UseCase(readOnly = true)
public class ViewAircraftForServicesUseCase {
    private final AircraftRepository repository;

    public ViewAircraftForServicesUseCase(AircraftRepository repository) {
        this.repository = repository;
    }

    public AircraftForServicesResponse execute(ViewAircraftDetailsRequest request) {
        return repository.findByRegistrationNumber(request.registrationNumber())
                .map(AircraftForServicesResponse::from)
                .orElseThrow(() -> new AircraftNotFoundException(
                        "Aircraft not found with registration: " + request.registrationNumber().getNumber()));
    }
}
