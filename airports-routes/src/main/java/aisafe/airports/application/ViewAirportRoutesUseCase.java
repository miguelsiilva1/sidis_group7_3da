package aisafe.airports.application;

import aisafe.shared.application.UseCase;
import aisafe.airports.domain.AirportNotFoundException;
import aisafe.airports.domain.AirportRepository;
import aisafe.airports.domain.IataCode;
import aisafe.routes.application.dtos.RouteResponse;
import aisafe.routes.application.RoutesAcrossReplicas;

import java.util.List;

/**
 * Routes that start or end at an airport (US209), gathered from every replica.
 */
@UseCase(readOnly = true)
public class ViewAirportRoutesUseCase {
    private final AirportRepository airportRepository;
    private final RoutesAcrossReplicas routes;

    public ViewAirportRoutesUseCase(AirportRepository airportRepository, RoutesAcrossReplicas routes) {
        this.airportRepository = airportRepository;
        this.routes = routes;
    }

    public List<RouteResponse> execute(String iataCode) {
        IataCode code = new IataCode(iataCode);
        if (!airportRepository.existsByIataCode(code)) {
            throw new AirportNotFoundException(iataCode);
        }
        return routes.findAll().stream()
                .filter(v -> v.value().getOrigin().equals(code) || v.value().getDestination().equals(code))
                .map(v -> RouteResponse.from(v.value(), v.version()))
                .toList();
    }
}
