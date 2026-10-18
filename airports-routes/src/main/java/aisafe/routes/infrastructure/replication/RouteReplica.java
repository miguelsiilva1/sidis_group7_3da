package aisafe.routes.infrastructure.replication;

import aisafe.airports.domain.IataCode;
import aisafe.routes.domain.Route;
import aisafe.routes.domain.RouteHistory;
import aisafe.routes.domain.RouteStatus;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * A route as copied to the other holder (record: held by its home replica and the next one),
 * together with its history, so a route and its history always live on the same replicas.
 */
public record RouteReplica(
        String origin,
        String destination,
        Integer estimatedFlightTime,
        Double minimumRange,
        Integer minimumCapacity,
        RouteStatus status,
        List<HistoryEntry> history
) {
    public static String keyOf(String origin, String destination) {
        return origin + "-" + destination;
    }

    public static RouteReplica from(Route route, List<RouteHistory> history) {
        return new RouteReplica(route.getOrigin().getCode(), route.getDestination().getCode(),
                route.getEstimatedFlightTime(), route.getMinimumRange(), route.getMinimumCapacity(), route.getStatus(),
                history.stream()
                        .sorted(Comparator.comparing(RouteHistory::getChangedAt))
                        .map(h -> new HistoryEntry(h.getChangeDescription(), h.getChangedAt(), h.getChangedBy()))
                        .toList());
    }

    public Route toRoute() {
        Route route = new Route(origin, destination, estimatedFlightTime, minimumRange, minimumCapacity);
        route.changeStatus(status);
        return route;
    }

    public List<RouteHistory> toHistory() {
        return history.stream()
                .map(h -> new RouteHistory(new IataCode(origin), new IataCode(destination),
                        h.changeDescription(), h.changedAt(), h.changedBy()))
                .toList();
    }

    public record HistoryEntry(String changeDescription, LocalDateTime changedAt, String changedBy) {
    }
}
