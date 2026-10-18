package aisafe.routes.infrastructure.replication;

import aisafe.airports.domain.IataCode;
import aisafe.routes.domain.Route;
import aisafe.routes.domain.RouteHistoryRepository;
import aisafe.routes.domain.RouteRepository;
import aisafe.routes.infrastructure.persistence.jpa.SpringDataRouteRepository;
import aisafe.shared.infrastructure.replication.ReplicaHandler;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Applies route replicas (record): the route and its whole history. Which change wins is decided
 * before, by its stamp ({@code ReplicaApplier}). After applying, the local version is set to the
 * sender's, so both holders give the same ETag.
 */
@Component
public class RouteReplicaHandler implements ReplicaHandler<RouteReplica> {

    public static final String TYPE = "routes";

    private final RouteRepository routeRepository;
    private final RouteHistoryRepository historyRepository;
    private final SpringDataRouteRepository springRepo;

    public RouteReplicaHandler(RouteRepository routeRepository, RouteHistoryRepository historyRepository,
                               SpringDataRouteRepository springRepo) {
        this.routeRepository = routeRepository;
        this.historyRepository = historyRepository;
        this.springRepo = springRepo;
    }

    @Override public String type() { return TYPE; }
    @Override public int order() { return 40; }
    @Override public boolean isCatalog() { return false; }
    @Override public Class<RouteReplica> payloadType() { return RouteReplica.class; }

    @Override
    public void apply(String key, RouteReplica payload, Long version) {
        routeRepository.save(payload.toRoute());
        historyRepository.deleteAllByRoute(payload.origin(), payload.destination());
        payload.toHistory().forEach(historyRepository::save);
        if (version != null) {
            springRepo.alignVersion(payload.origin(), payload.destination(), version);
        }
    }

    @Override
    public void delete(String key) {
        String[] codes = key.split("-", 2);
        routeRepository.findByOriginAndDestination(new IataCode(codes[0]), new IataCode(codes[1])).ifPresent(route -> {
            historyRepository.deleteAllByRoute(codes[0], codes[1]);
            routeRepository.delete(route);
        });
    }

    @Override
    public List<Item<RouteReplica>> exportAll() {
        return routeRepository.findAll().stream()
                .map(route -> new Item<>(
                        RouteReplica.keyOf(route.getOrigin().getCode(), route.getDestination().getCode()),
                        RouteReplica.from(route, historyRepository.findAllByRoute(
                                route.getOrigin().getCode(), route.getDestination().getCode())),
                        routeRepository.findVersionFor(route.getOrigin(), route.getDestination())))
                .toList();
    }
}
