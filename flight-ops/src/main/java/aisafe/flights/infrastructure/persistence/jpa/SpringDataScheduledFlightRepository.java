package aisafe.flights.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface SpringDataScheduledFlightRepository extends JpaRepository<ScheduledFlightJpaEntity, Long> {

    Optional<ScheduledFlightJpaEntity> findByAircraftRegistrationAndDepartureDateTime(String aircraftRegistration, OffsetDateTime departureDateTime);

    @Query("SELECT f.version FROM ScheduledFlightJpaEntity f WHERE f.id = :id")
    Long findVersionFor(@Param("id") Long id);

    /**
     * Sets the version of a replica to the version it has on the replica that sent it, so both
     * holders of the flight give the same version.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ScheduledFlightJpaEntity f SET f.version = :version WHERE f.id = :id")
    int alignVersion(@Param("id") Long id, @Param("version") Long version);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ScheduledFlightJpaEntity f SET f.version = :version WHERE f.aircraftRegistration = :aircraftRegistration AND f.departureDateTime = :departureDateTime")
    int alignVersionByNaturalKey(@Param("aircraftRegistration") String aircraftRegistration,
                                 @Param("departureDateTime") OffsetDateTime departureDateTime,
                                 @Param("version") Long version);
}
