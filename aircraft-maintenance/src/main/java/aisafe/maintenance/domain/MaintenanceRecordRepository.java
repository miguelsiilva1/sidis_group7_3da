package aisafe.maintenance.domain;

import aisafe.shared.domain.BaseRepository;

import java.util.Optional;
import java.util.UUID;

public interface MaintenanceRecordRepository extends BaseRepository<MaintenanceRecord> {
    Optional<MaintenanceRecord> findByRecordId(UUID recordId);
    Long findVersionFor(UUID recordId);
}
