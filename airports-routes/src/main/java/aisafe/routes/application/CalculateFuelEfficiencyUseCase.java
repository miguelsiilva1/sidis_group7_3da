package aisafe.routes.application;

import aisafe.airports.domain.IataCode;
import aisafe.fleet.application.Fleet;
import aisafe.fleet.domain.AircraftInfo;
import aisafe.fleet.domain.AircraftNotFoundException;
import aisafe.routes.application.dtos.FuelEfficiencyResponse;
import aisafe.routes.application.dtos.RouteForServicesResponse;
import aisafe.routes.domain.Route;
import aisafe.routes.domain.RouteNotFoundException;
import aisafe.routes.domain.RouteRepository;
import aisafe.shared.application.PeerQueries;
import aisafe.shared.application.UseCase;

/**
 * Calculates the fuel an aircraft needs for a route: its consumption per distance unit times the
 * route's minimum range (US227).
 *
 * <p>Moved here from aircraft-maintenance (decision D2): the aircraft is asked to
 * aircraft-maintenance (contract C1); the route is read here or, when another replica holds it,
 * from the peers.</p>
 */
@UseCase(readOnly = true)
public class CalculateFuelEfficiencyUseCase {

    private final Fleet fleet;
    private final RouteRepository routeRepository;
    private final PeerQueries peerQueries;

    public CalculateFuelEfficiencyUseCase(Fleet fleet, RouteRepository routeRepository, PeerQueries peerQueries) {
        this.fleet = fleet;
        this.routeRepository = routeRepository;
        this.peerQueries = peerQueries;
    }

    public FuelEfficiencyResponse execute(String registration, String origin, String destination) {
        String originCode = origin.trim().toUpperCase();
        String destinationCode = destination.trim().toUpperCase();
        double minimumRange = routeRepository.findByOriginAndDestination(new IataCode(originCode), new IataCode(destinationCode))
                .map(Route::getMinimumRange)
                .or(() -> peerQueries.fetchFirst("/internal/routes/" + originCode + "/" + destinationCode, RouteForServicesResponse.class)
                        .map(RouteForServicesResponse::minimumRange))
                .orElseThrow(() -> new RouteNotFoundException(originCode + " -> " + destinationCode));

        AircraftInfo aircraft = fleet.findAircraft(registration)
                .orElseThrow(() -> new AircraftNotFoundException(registration));
        Double consumption = aircraft.fuelConsumptionPerDistanceUnit();

        return new FuelEfficiencyResponse(
                aircraft.registrationNumber(),
                consumption,
                originCode,
                destinationCode,
                consumption == null ? null : consumption * minimumRange);
    }
}
