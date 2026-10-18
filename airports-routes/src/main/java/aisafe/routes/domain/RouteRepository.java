package aisafe.routes.domain;

import aisafe.airports.domain.IataCode;
import aisafe.shared.domain.BaseRepository;

import java.util.Optional;

/**
 * Routes held by this replica. Listings and reports over every replica read through
 * {@code RoutesAcrossReplicas}.
 */
public interface RouteRepository extends BaseRepository<Route> {
    Optional<Route> findByOriginAndDestination(IataCode origin, IataCode destination);
    boolean existsByOriginAndDestination(IataCode origin, IataCode destination);
    Long findVersionFor(IataCode origin, IataCode destination);
}
