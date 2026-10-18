package aisafe.routes.application;

import aisafe.routes.application.dtos.TotalDistanceResponse;
import aisafe.routes.domain.RouteStatus;
import aisafe.shared.application.Versioned;
import aisafe.shared.application.UseCase;
import lombok.RequiredArgsConstructor;

/**
 * Total distance of the active routes of every replica (US215).
 */
@UseCase(readOnly = true)
@RequiredArgsConstructor
public class CalculateTotalNetworkDistanceUseCase {

    private final RoutesAcrossReplicas routes;
    private final RouteDistanceService routeDistanceService;

    public TotalDistanceResponse execute() {
        double totalDistance = routes.findAll().stream()
                .map(Versioned::value)
                .filter(route -> route.getStatus() == RouteStatus.ACTIVE)
                .mapToDouble(routeDistanceService::calculateDistanceKm)
                .sum();
        return new TotalDistanceResponse(Math.round(totalDistance * 100.0) / 100.0, "km");
    }
}
