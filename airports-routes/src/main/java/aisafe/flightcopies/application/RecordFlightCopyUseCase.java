package aisafe.flightcopies.application;

import aisafe.airports.domain.IataCode;
import aisafe.flightcopies.application.dtos.FlightCopyResponse;
import aisafe.flightcopies.application.dtos.RecordFlightCopyRequest;
import aisafe.flightcopies.domain.FlightCopy;
import aisafe.flightcopies.domain.FlightCopyRepository;
import aisafe.flightcopies.domain.FlightCopyStatus;
import aisafe.flightcopies.domain.RouteUnavailableForFlightException;
import aisafe.routes.application.dtos.RouteForServicesResponse;
import aisafe.routes.domain.Route;
import aisafe.routes.domain.RouteNotFoundException;
import aisafe.routes.domain.RouteRepository;
import aisafe.routes.domain.RouteStatus;
import aisafe.shared.application.PeerQueries;
import aisafe.shared.application.UseCase;
import aisafe.shared.domain.DomainException;

import java.util.Optional;

/**
 * Stores the copy of a flight sent by flight-ops (contract C5).
 *
 * <p>Receiving the copy of a new future flight is the "confirm the route" step of the flight
 * scheduling Saga: it is rejected if the route no longer exists (404) or is no longer active (409),
 * and flight-ops then undoes the scheduling. Copies are idempotent: sending the same copy again,
 * or an older version, changes nothing.</p>
 *
 * <p>The route may live on another replica (data is partitioned), so it is looked up on the
 * peers when it is not held locally.</p>
 */
@UseCase
public class RecordFlightCopyUseCase {

    private final FlightCopyRepository flightCopyRepository;
    private final RouteRepository routeRepository;
    private final PeerQueries peerQueries;

    public RecordFlightCopyUseCase(FlightCopyRepository flightCopyRepository,
                                   RouteRepository routeRepository,
                                   PeerQueries peerQueries) {
        this.flightCopyRepository = flightCopyRepository;
        this.routeRepository = routeRepository;
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
                request.aircraftRegistration().trim().toUpperCase(),
                request.version());

        Optional<FlightCopy> stored = flightCopyRepository.findById(copy.getId());
        if (stored.isPresent() && !copy.supersedes(stored.get())) {
            return FlightCopyResponse.from(stored.get());
        }
        if (stored.isEmpty() && !copy.isCanceled()) {
            checkRoute(copy);
        }
        return FlightCopyResponse.from(flightCopyRepository.save(copy));
    }

    private void checkRoute(FlightCopy copy) {
        String origin = copy.getOriginCode();
        String destination = copy.getDestinationCode();
        RouteStatus status = routeRepository.findByOriginAndDestination(new IataCode(origin), new IataCode(destination))
                .map(Route::getStatus)
                .or(() -> peerQueries.fetchFirst("/internal/routes/" + origin + "/" + destination, RouteForServicesResponse.class)
                        .map(RouteForServicesResponse::status))
                .orElseThrow(() -> new RouteNotFoundException(origin + "-" + destination));
        boolean futureFlight = copy.getStatus() == FlightCopyStatus.SCHEDULED || copy.getStatus() == FlightCopyStatus.DELAYED;
        if (futureFlight && status != RouteStatus.ACTIVE) {
            throw new RouteUnavailableForFlightException(origin, destination);
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
