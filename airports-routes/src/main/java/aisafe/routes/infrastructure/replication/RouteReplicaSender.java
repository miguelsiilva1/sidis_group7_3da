package aisafe.routes.infrastructure.replication;

import aisafe.routes.infrastructure.persistence.jpa.RouteHistoryMapper;
import aisafe.routes.infrastructure.persistence.jpa.RouteJpaEntity;
import aisafe.routes.infrastructure.persistence.jpa.RouteMapper;
import aisafe.routes.infrastructure.persistence.jpa.SpringDataRouteHistoryRepository;
import aisafe.shared.infrastructure.replication.Replicator;
import org.springframework.stereotype.Component;

/**
 * Hands a route that was just saved, with its whole history, to the {@link Replicator}. Called
 * when the route changes and when an entry is added to its history.
 */
@Component
public class RouteReplicaSender {

    private final SpringDataRouteHistoryRepository historyRepo;
    private final Replicator replicator;

    public RouteReplicaSender(SpringDataRouteHistoryRepository historyRepo, Replicator replicator) {
        this.historyRepo = historyRepo;
        this.replicator = replicator;
    }

    public void routeSaved(RouteJpaEntity route) {
        String origin = route.getOriginCode().getCode();
        String destination = route.getDestinationCode().getCode();
        RouteReplica replica = RouteReplica.from(RouteMapper.toDomain(route),
                historyRepo.findAllByRoute(origin, destination).stream().map(RouteHistoryMapper::toDomain).toList());
        // Record: copied to the other holder (home replica and its successor)
        replicator.recordSaved(RouteReplicaHandler.TYPE, RouteReplica.keyOf(origin, destination), replica, route.getVersion());
    }

    public void routeDeleted(String origin, String destination) {
        replicator.recordDeleted(RouteReplicaHandler.TYPE, RouteReplica.keyOf(origin, destination));
    }
}
