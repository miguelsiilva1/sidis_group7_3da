package aisafe.routes.application;

import aisafe.flightcopies.application.FlightCopiesAcrossReplicas;
import aisafe.flightcopies.domain.FlightCopy;
import aisafe.routes.application.dtos.ActiveRouteResponse;
import aisafe.routes.domain.InvalidSortParameterException;
import aisafe.routes.domain.Route;
import aisafe.routes.domain.RouteStatus;
import aisafe.shared.application.UseCase;
import aisafe.shared.application.Versioned;
import aisafe.shared.domain.InvalidListingCriteriaException;
import lombok.RequiredArgsConstructor;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lists the active routes sorted by distance or by popularity (US214).
 *
 * <p>Popularity is the number of flights on the route, counted on the copies of the flights that
 * flight-ops sends (contract C5). Routes and flight copies are both gathered from every replica
 * and counted once (decision D5).</p>
 */
@UseCase(readOnly = true)
@RequiredArgsConstructor
public class ListActiveRoutesUseCase {

    private final RoutesAcrossReplicas routes;
    private final FlightCopiesAcrossReplicas flightCopies;
    private final RouteDistanceService routeDistanceService;

    public List<ActiveRouteResponse> execute(String status, String sortBy) {
        if (status != null && !status.equalsIgnoreCase("active")) {
            throw new InvalidListingCriteriaException("Only active route listing is supported.");
        }
        String normalizedSort = sortBy == null ? "distance" : sortBy.trim().toLowerCase();
        if (!normalizedSort.equals("distance") && !normalizedSort.equals("popularity")) {
            throw new InvalidSortParameterException("sortBy must be distance or popularity.");
        }

        Comparator<ActiveRouteResponse> comparator = normalizedSort.equals("popularity")
                ? Comparator.comparing(ActiveRouteResponse::popularity).reversed()
                : Comparator.comparing(ActiveRouteResponse::distanceKm);

        List<Route> active = routes.findAll().stream()
                .map(Versioned::value)
                .filter(route -> route.getStatus() == RouteStatus.ACTIVE)
                .toList();
        if (active.isEmpty()) {
            return List.of();
        }
        Map<String, Long> flightsPerRoute = flightCopies.findAll().stream()
                .collect(Collectors.groupingBy(ListActiveRoutesUseCase::routeOf, Collectors.counting()));

        return active.stream()
                .map(route -> toResponse(route, flightsPerRoute.getOrDefault(routeOf(route), 0L)))
                .sorted(comparator)
                .toList();
    }

    private ActiveRouteResponse toResponse(Route route, long popularity) {
        return new ActiveRouteResponse(
                route.getOrigin().getCode(),
                route.getDestination().getCode(),
                route.getEstimatedFlightTime(),
                route.getMinimumRange(),
                route.getMinimumCapacity(),
                route.getStatus(),
                routeDistanceService.calculateDistanceKm(route),
                popularity
        );
    }

    private static String routeOf(Route route) {
        return route.getOrigin().getCode() + "-" + route.getDestination().getCode();
    }

    private static String routeOf(FlightCopy copy) {
        return copy.getOriginCode() + "-" + copy.getDestinationCode();
    }
}
