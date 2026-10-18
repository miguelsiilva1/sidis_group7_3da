package aisafe.maintenance.infrastructure.replication;

import aisafe.maintenance.domain.MaintenancePartRepository;
import aisafe.shared.infrastructure.replication.ReplicaHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Applies maintenance part replicas (catalog).
 */
@Component
public class MaintenancePartReplicaHandler implements ReplicaHandler<MaintenancePartReplica> {

    public static final String TYPE = "maintenance-parts";
    private static final Logger log = LoggerFactory.getLogger(MaintenancePartReplicaHandler.class);

    private final MaintenancePartRepository partRepository;

    public MaintenancePartReplicaHandler(MaintenancePartRepository partRepository) {
        this.partRepository = partRepository;
    }

    @Override public String type() { return TYPE; }
    @Override public int order() { return 20; }
    @Override public boolean isCatalog() { return true; }
    @Override public Class<MaintenancePartReplica> payloadType() { return MaintenancePartReplica.class; }

    @Override
    public void apply(String key, MaintenancePartReplica payload, Long version) {
        partRepository.save(payload.toDomain());
    }

    @Override
    public void delete(String key) {
        try {
            partRepository.findByPartNumber(key).ifPresent(partRepository::delete);
        } catch (DataIntegrityViolationException e) {
            // A maintenance record on this replica still uses the part: keep it rather than block replication.
            log.warn("Maintenance part {} not deleted on this replica: still in use", key);
        }
    }

    @Override
    public List<Item<MaintenancePartReplica>> exportAll() {
        return partRepository.findAll().stream()
                .map(p -> new Item<>(p.getPartNumber(), MaintenancePartReplica.from(p), null))
                .toList();
    }
}
