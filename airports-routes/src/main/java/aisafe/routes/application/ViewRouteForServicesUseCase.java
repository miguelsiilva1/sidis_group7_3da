package aisafe.routes.application;

import aisafe.airports.domain.IataCode;
import aisafe.routes.application.dtos.RouteForServicesResponse;
import aisafe.routes.domain.Route;
import aisafe.routes.domain.RouteNotFoundException;
import aisafe.routes.domain.RouteRepository;
import aisafe.shared.application.UseCase;

/**
 * Returns what other services need to know about a route held by this replica, with its distance
 * (contract C2). When this replica does not hold it, the not-found answer makes the request go to
 * the peers.
 */
@UseCase(readOnly = true)
public class ViewRouteForServicesUseCase {

    private final RouteRepository routeRepository;
    private final RouteDistanceService routeDistanceService;

    public ViewRouteForServicesUseCase(RouteRepository routeRepository, RouteDistanceService routeDistanceService) {
        this.routeRepository = routeRepository;
        this.routeDistanceService = routeDistanceService;
    }

    public RouteForServicesResponse execute(String origin, String destination) {
        String originCode = origin.trim().toUpperCase();
        String destinationCode = destination.trim().toUpperCase();
        Route route = routeRepository.findByOriginAndDestination(new IataCode(originCode), new IataCode(destinationCode))
                .orElseThrow(() -> new RouteNotFoundException(originCode + "-" + destinationCode));
        return RouteForServicesResponse.from(route, routeDistanceService.calculateDistanceKm(route));
    }
}
