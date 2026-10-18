package aisafe.routes.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SpringDataRouteRepository extends JpaRepository<RouteJpaEntity, Long> {
    Optional<RouteJpaEntity> findByOriginCode_CodeAndDestinationCode_Code(String originCode, String destinationCode);
    boolean existsByOriginCode_CodeAndDestinationCode_Code(String originCode, String destinationCode);

    /**
     * Sets the version of a replica to the version it has on the replica that sent it, so both
     * holders of the route give the same ETag.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RouteJpaEntity r SET r.version = :version WHERE r.originCode.code = :origin AND r.destinationCode.code = :destination")
    int alignVersion(@Param("origin") String origin, @Param("destination") String destination, @Param("version") Long version);
}
