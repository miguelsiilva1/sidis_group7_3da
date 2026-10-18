package aisafe.flightcopies.application;

import aisafe.aircrafts.application.dtos.AircraftForServicesResponse;
import aisafe.aircrafts.domain.Aircraft;
import aisafe.aircrafts.domain.AircraftNotFoundException;
import aisafe.aircrafts.domain.AircraftRepository;
import aisafe.aircrafts.domain.AircraftStatus;
import aisafe.aircrafts.domain.RegistrationNumber;
import aisafe.flightcopies.application.dtos.FlightCopyResponse;
import aisafe.flightcopies.application.dtos.RecordFlightCopyRequest;
import aisafe.flightcopies.domain.AircraftUnavailableForFlightException;
import aisafe.flightcopies.domain.FlightCopy;
import aisafe.flightcopies.domain.FlightCopyRepository;
import aisafe.flightcopies.domain.FlightCopyStatus;
import aisafe.shared.application.PeerQueries;
import aisafe.shared.application.UseCase;
import aisafe.shared.domain.DomainException;

import java.util.Optional;

/**
 * Stores the copy of a flight sent by flight-ops (contract C4).
 *
 * <p>Receiving the copy of a new future flight is the "reserve the aircraft" step of the flight
 * scheduling Saga: it is rejected if the aircraft no longer exists (404) or cannot take a new
 * flight (409), and flight-ops then undoes the scheduling. Copies are idempotent: sending the
 * same copy again, or an older version, changes nothing.</p>
 *
 * <p>The aircraft may live on another replica (data is partitioned), so it is looked up on the
 * peers when it is not held locally.</p>
 */
@UseCase
public class RecordFlightCopyUseCase {

    private final FlightCopyRepository flightCopyRepository;
    private final AircraftRepository aircraftRepository;
    private final PeerQueries peerQueries;

    public RecordFlightCopyUseCase(FlightCopyRepository flightCopyRepository,
                                   AircraftRepository aircraftRepository,
                                   PeerQueries peerQueries) {
        this.flightCopyRepository = flightCopyRepository;
        this.aircraftRepository = aircraftRepository;
        this.peerQueries = peerQueries;
    }

    public FlightCopyResponse execute(RecordFlightCopyRequest request) {
        FlightCopy copy = new FlightCopy(
                request.id(),
                request.departureDateTime(),
                request.arrivalDateTime(),
                parseStatus(request.status()),
                request.originIataCode().trim().toUpperCase(),
                request.destinationIataCode().trim().toUpperCase(),
                new RegistrationNumber(request.aircraftRegistration().trim().toUpperCase()),
                request.version());

        Optional<FlightCopy> stored = flightCopyRepository.findById(copy.getId());
        if (stored.isPresent() && !copy.supersedes(stored.get())) {
            return FlightCopyResponse.from(stored.get());
        }
        if (stored.isEmpty() && !copy.isCanceled()) {
            checkAircraft(copy);
        }
        return FlightCopyResponse.from(flightCopyRepository.save(copy));
    }

    private void checkAircraft(FlightCopy copy) {
        RegistrationNumber registration = copy.getAircraftRegistrationNumber();
        AircraftStatus status = aircraftRepository.findByRegistrationNumber(registration)
                .map(Aircraft::getStatus)
                .or(() -> peerQueries.fetchFirst("/internal/aircrafts/" + registration.getNumber(), AircraftForServicesResponse.class)
                        .map(AircraftForServicesResponse::status))
                .orElseThrow(() -> new AircraftNotFoundException("Aircraft not found: " + registration.getNumber()));
        boolean futureFlight = copy.getStatus() == FlightCopyStatus.SCHEDULED || copy.getStatus() == FlightCopyStatus.DELAYED;
        if (futureFlight && (status == AircraftStatus.INACTIVE || status == AircraftStatus.UNDER_MAINTENANCE)) {
            throw new AircraftUnavailableForFlightException(registration.getNumber());
        }
    }

    private static FlightCopyStatus parseStatus(String status) {
        try {
            return FlightCopyStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new DomainException("Invalid flight status: " + status);
        }
    }
}
