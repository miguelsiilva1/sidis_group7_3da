package aisafe.aircrafts.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SpringDataAircraftRepository extends JpaRepository<AircraftJpaEntity, Long> {

    Optional<AircraftJpaEntity> findByRegistrationNumber(RegistrationNumberJpaEmbeddable registrationNumber);
    boolean existsByRegistrationNumber(RegistrationNumberJpaEmbeddable registrationNumber);

    /**
     * Sets the version of a replica to the version it has on the replica that sent it, so both
     * holders of the record give the same ETag.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AircraftJpaEntity a SET a.version = :version WHERE a.registrationNumber.number = :key")
    int alignVersion(@Param("key") String key,
                     @Param("version") Long version);
}