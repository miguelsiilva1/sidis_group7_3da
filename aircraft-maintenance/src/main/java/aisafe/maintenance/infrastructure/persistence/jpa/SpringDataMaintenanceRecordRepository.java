package aisafe.maintenance.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataMaintenanceRecordRepository extends JpaRepository<MaintenanceRecordJpaEntity, Long> {
    Optional<MaintenanceRecordJpaEntity> findByRecordId(UUID recordId);

    @Query("SELECT m.version FROM MaintenanceRecordJpaEntity m WHERE m.recordId = :recordId")
    Long findVersionFor(UUID recordId);

    /**
     * Sets the version of a replica to the version it has on the replica that sent it, so both
     * holders of the record give the same ETag.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE MaintenanceRecordJpaEntity a SET a.version = :version WHERE a.recordId = :key")
    int alignVersion(@Param("key") UUID key,
                     @Param("version") Long version);
}
