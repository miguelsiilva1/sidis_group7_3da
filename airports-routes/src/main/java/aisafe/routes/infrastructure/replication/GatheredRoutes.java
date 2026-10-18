package aisafe.routes.infrastructure.replication;

import aisafe.routes.application.RoutesAcrossReplicas;
import aisafe.routes.domain.Route;
import aisafe.shared.application.Versioned;
import aisafe.shared.infrastructure.replication.ReplicaGatherer;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link RoutesAcrossReplicas} built from the routes held here and by the peers.
 */
@Component
public class GatheredRoutes implements RoutesAcrossReplicas {

    private final ReplicaGatherer gatherer;
    private final RouteReplicaHandler handler;

    public GatheredRoutes(ReplicaGatherer gatherer, RouteReplicaHandler handler) {
        this.gatherer = gatherer;
        this.handler = handler;
    }

    @Override
    public List<Versioned<Route>> findAll() {
        return gatherer.gather(handler).stream()
                .map(item -> new Versioned<>(item.payload().toRoute(), item.version()))
                .toList();
    }
}
