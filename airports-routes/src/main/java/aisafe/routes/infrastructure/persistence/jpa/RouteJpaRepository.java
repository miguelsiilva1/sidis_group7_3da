package aisafe.routes.infrastructure.persistence.jpa;

import aisafe.airports.domain.IataCode;
import aisafe.routes.domain.Route;
import aisafe.routes.domain.RouteRepository;
import aisafe.routes.infrastructure.replication.RouteReplicaSender;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@Profile("jpa")
public class RouteJpaRepository implements RouteRepository {

    private final SpringDataRouteRepository springRepo;
    private final RouteReplicaSender replicaSender;

    public RouteJpaRepository(SpringDataRouteRepository springRepo, RouteReplicaSender replicaSender) {
        this.springRepo = springRepo;
        this.replicaSender = replicaSender;
    }

    @Override
    public long count() {
        return springRepo.count();
    }

    @Override
    public Route save(Route route) {
        Optional<RouteJpaEntity> existing = springRepo.findByOriginCode_CodeAndDestinationCode_Code(
                route.getOrigin().getCode(), route.getDestination().getCode());
        RouteJpaEntity jpaEntity = RouteMapper.toJpa(route);
        existing.ifPresent(e -> {
            jpaEntity.setId(e.getId());
            jpaEntity.setVersion(e.getVersion());
        });
        replicaSender.routeSaved(springRepo.saveAndFlush(jpaEntity));
        return route;
    }

    @Override
    public Long findVersionFor(IataCode origin, IataCode destination) {
        return springRepo.findByOriginCode_CodeAndDestinationCode_Code(origin.getCode(), destination.getCode())
                .map(RouteJpaEntity::getVersion)
                .orElse(0L);
    }

    @Override
    public Optional<Route> findByOriginAndDestination(IataCode origin, IataCode destination) {
        return springRepo.findByOriginCode_CodeAndDestinationCode_Code(origin.getCode(), destination.getCode())
                .map(RouteMapper::toDomain);
    }

    @Override
    public void delete(Route route) {
        springRepo.findByOriginCode_CodeAndDestinationCode_Code(
                route.getOrigin().getCode(), route.getDestination().getCode())
                .ifPresent(entity -> {
                    springRepo.delete(entity);
                    replicaSender.routeDeleted(route.getOrigin().getCode(), route.getDestination().getCode());
                });
    }

    @Override
    public List<Route> findAll() {
        return springRepo.findAll().stream()
                .map(RouteMapper::toDomain)
                .collect(Collectors.toList());
    }






    @Override
    public boolean existsByOriginAndDestination(IataCode origin, IataCode destination) {
        return springRepo.existsByOriginCode_CodeAndDestinationCode_Code(origin.getCode(), destination.getCode());
    }



}
