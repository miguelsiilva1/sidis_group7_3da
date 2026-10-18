package aisafe.routes.infrastructure.persistence.jpa;

import aisafe.routes.domain.RouteHistory;
import aisafe.routes.domain.RouteHistoryRepository;
import aisafe.routes.domain.RouteNotFoundException;
import aisafe.routes.infrastructure.replication.RouteReplicaSender;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
@Profile("jpa")
public class RouteHistoryJpaRepository implements RouteHistoryRepository {

    private final SpringDataRouteHistoryRepository springRepo;
    private final SpringDataRouteRepository routeSpringRepo;
    private final RouteReplicaSender replicaSender;

    public RouteHistoryJpaRepository(SpringDataRouteHistoryRepository springRepo,
                                      SpringDataRouteRepository routeSpringRepo,
                                      RouteReplicaSender replicaSender) {
        this.springRepo = springRepo;
        this.routeSpringRepo = routeSpringRepo;
        this.replicaSender = replicaSender;
    }

    @Override
    public RouteHistory save(RouteHistory history) {
        RouteJpaEntity routeJpa = routeSpringRepo.findByOriginCode_CodeAndDestinationCode_Code(
                        history.getOriginCode().getCode(), history.getDestinationCode().getCode())
                .orElseThrow(() -> new RouteNotFoundException(
                        history.getOriginCode().getCode() + "-" + history.getDestinationCode().getCode()));
        springRepo.saveAndFlush(RouteHistoryMapper.toJpa(history, routeJpa));
        // The history travels with its route: the route is copied again, with the new entry
        replicaSender.routeSaved(routeJpa);
        return history;
    }

    @Override
    public List<RouteHistory> findAllByRoute(String originCode, String destinationCode) {
        return springRepo.findAllByRoute(originCode, destinationCode).stream()
                .map(RouteHistoryMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteAllByRoute(String originCode, String destinationCode) {
        springRepo.deleteAllByRoute(originCode, destinationCode);
    }

    @Override
    public long count() {
        return springRepo.count();
    }

    @Override
    public List<RouteHistory> findAll() {
        return springRepo.findAll().stream()
                .map(RouteHistoryMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(RouteHistory history) {
        springRepo.findAllByRoute(history.getOriginCode().getCode(), history.getDestinationCode().getCode()).stream()
                .filter(e -> e.getChangedAt().equals(history.getChangedAt())
                        && e.getChangedBy().equals(history.getChangedBy()))
                .findFirst()
                .ifPresent(springRepo::delete);
    }
}
