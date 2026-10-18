package aisafe.airports.application;

import aisafe.shared.application.UseCase;
import aisafe.airports.application.dtos.AirportStatisticsResponse;
import aisafe.airports.domain.AirportRepository;
import aisafe.routes.application.RoutesAcrossReplicas;
import aisafe.routes.domain.Route;
import aisafe.shared.application.Versioned;

import java.util.Comparator;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import java.util.List;

/**
 * Number of routes per airport, most connected first (US210). Airports are on every replica; the
 * routes are gathered from every replica and counted once (decision D5).
 */
@UseCase(readOnly = true)
public class GetAirportStatisticsUseCase {
    private final AirportRepository airportRepository;
    private final RoutesAcrossReplicas routes;

    public GetAirportStatisticsUseCase(AirportRepository airportRepository, RoutesAcrossReplicas routes) {
        this.airportRepository = airportRepository;
        this.routes = routes;
    }

    public List<AirportStatisticsResponse> execute() {
        // A route from A to A cannot exist, so each route counts once for each of its two airports
        Map<String, Long> routesPerAirport = routes.findAll().stream()
                .map(Versioned::value)
                .flatMap(GetAirportStatisticsUseCase::airportsOf)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return airportRepository.findAll().stream()
                .map(a -> new AirportStatisticsResponse(a.getIataCode().getCode(), a.getName(), a.getCity(), a.getCountry(),
                        routesPerAirport.getOrDefault(a.getIataCode().getCode(), 0L)))
                .sorted(Comparator.comparingLong(AirportStatisticsResponse::routeCount).reversed()
                        .thenComparing(AirportStatisticsResponse::iataCode))
                .toList();
    }

    private static Stream<String> airportsOf(Route route) {
        return Stream.of(route.getOrigin().getCode(), route.getDestination().getCode());
    }
}
