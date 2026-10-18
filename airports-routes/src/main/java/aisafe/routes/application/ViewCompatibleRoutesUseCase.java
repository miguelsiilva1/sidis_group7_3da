package aisafe.routes.application;

import aisafe.fleet.application.Fleet;
import aisafe.fleet.domain.AircraftInfo;
import aisafe.fleet.domain.AircraftNotFoundException;
import aisafe.routes.application.dtos.CompatibleRouteResponse;
import aisafe.routes.domain.Route;
import aisafe.routes.domain.RouteStatus;
import aisafe.shared.application.UseCase;
import aisafe.shared.application.Versioned;

import java.util.List;

/**
 * Views which active routes an aircraft can fly, based on its range and seat capacity (US203).
 *
 * <p>Moved here from aircraft-maintenance (decision D2): the aircraft is asked to
 * aircraft-maintenance (contract C1), and the routes are gathered from every replica.</p>
 */
@UseCase(readOnly = true)
public class ViewCompatibleRoutesUseCase {

    private final Fleet fleet;
    private final RoutesAcrossReplicas routes;

    public ViewCompatibleRoutesUseCase(Fleet fleet, RoutesAcrossReplicas routes) {
        this.fleet = fleet;
        this.routes = routes;
    }

    public List<CompatibleRouteResponse> execute(String registration) {
        AircraftInfo aircraft = fleet.findAircraft(registration)
                .orElseThrow(() -> new AircraftNotFoundException(registration));

        return routes.findAll().stream()
                .map(Versioned::value)
                .filter(route -> route.getStatus() == RouteStatus.ACTIVE)
                .filter(route -> route.getMinimumRange() <= aircraft.range()
                        && route.getMinimumCapacity() <= aircraft.seatCapacity())
                .map(ViewCompatibleRoutesUseCase::toResponse)
                .toList();
    }

    private static CompatibleRouteResponse toResponse(Route route) {
        return new CompatibleRouteResponse(
                route.getOrigin().getCode(),
                route.getDestination().getCode(),
                route.getEstimatedFlightTime(),
                route.getMinimumRange(),
                route.getMinimumCapacity());
    }
}
