package aisafe.routes.application;

import aisafe.shared.application.UseCase;
import aisafe.airports.domain.Airport;
import aisafe.airports.domain.AirportStatus;
import aisafe.airports.domain.AirportNotFoundException;
import aisafe.airports.domain.AirportRepository;
import aisafe.airports.domain.IataCode;
import aisafe.routes.domain.InvalidRouteException;
import aisafe.routes.application.dtos.CreateRouteRequest;
import aisafe.routes.application.dtos.RouteResponse;
import aisafe.routes.domain.Route;
import aisafe.routes.domain.RouteHistory;
import aisafe.routes.domain.RouteHistoryRepository;
import aisafe.routes.domain.RouteRepository;
import aisafe.routes.application.dtos.RouteForServicesResponse;
import aisafe.shared.application.PeerQueries;
import aisafe.shared.domain.DuplicateResourceException;

/**
 * Use case responsible for creating a new route.
 *
 * <p>The new route stays on the replica that created it (and is copied to the next one). Airports
 * are on every replica, so they are checked locally.</p>
 */
@UseCase
public class CreateRouteUseCase {

    private final RouteRepository routeRepository;
    private final AirportRepository airportRepository;
    private final RouteHistoryRepository routeHistoryRepository;
    private final PeerQueries peerQueries;

    public CreateRouteUseCase(RouteRepository routeRepository, AirportRepository airportRepository,
                              RouteHistoryRepository routeHistoryRepository, PeerQueries peerQueries) {
        this.routeRepository = routeRepository;
        this.airportRepository = airportRepository;
        this.routeHistoryRepository = routeHistoryRepository;
        this.peerQueries = peerQueries;
    }

    /**
     * Validates and persists a new route based on the provided request.
     *
     * @param request the data required to create the route
     * @return the created route response
     */
    public RouteResponse execute(CreateRouteRequest request) {
        String originCode = request.originIataCode().trim().toUpperCase();
        String destinationCode = request.destinationIataCode().trim().toUpperCase();

        Airport originAirport = airportRepository.findByIataCode(new IataCode(originCode))
                .orElseThrow(() -> new AirportNotFoundException(originCode));
        if (originAirport.getStatus() != AirportStatus.OPERATIONAL) {
            throw new InvalidRouteException("Origin airport is not operational: " + originCode);
        }

        Airport destinationAirport = airportRepository.findByIataCode(new IataCode(destinationCode))
                .orElseThrow(() -> new AirportNotFoundException(destinationCode));
        if (destinationAirport.getStatus() != AirportStatus.OPERATIONAL) {
            throw new InvalidRouteException("Destination airport is not operational: " + destinationCode);
        }
        // Routes are partitioned between replicas: the route must not exist on any of them
        // (best effort: during a network partition a duplicate can slip through, plan §4.4).
        if (routeRepository.existsByOriginAndDestination(new IataCode(originCode), new IataCode(destinationCode))
                || peerQueries.fetchFirst("/internal/routes/" + originCode + "/" + destinationCode, RouteForServicesResponse.class).isPresent()) {
            throw new DuplicateResourceException("Route already exists between origin and destination.");
        }

        Route route = new Route(
                originCode,
                destinationCode,
                request.estimatedFlightTime(),
                request.minimumRange(),
                request.minimumCapacity()
        );

        routeRepository.save(route);
        routeHistoryRepository.save(new RouteHistory(new IataCode(originCode), new IataCode(destinationCode), "Route created", request.createdBy()));
        return RouteResponse.from(route);
    }
}
