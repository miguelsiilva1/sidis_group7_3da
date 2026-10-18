package aisafe.maintenance.infrastructure.replication;

import aisafe.maintenance.domain.MaintenanceTemplateRepository;
import aisafe.shared.infrastructure.replication.ReplicaHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Applies maintenance template replicas (catalog).
 */
@Component
public class MaintenanceTemplateReplicaHandler implements ReplicaHandler<MaintenanceTemplateReplica> {

    public static final String TYPE = "maintenance-templates";
    private static final Logger log = LoggerFactory.getLogger(MaintenanceTemplateReplicaHandler.class);

    private final MaintenanceTemplateRepository templateRepository;

    public MaintenanceTemplateReplicaHandler(MaintenanceTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    @Override public String type() { return TYPE; }
    @Override public int order() { return 30; }
    @Override public boolean isCatalog() { return true; }
    @Override public Class<MaintenanceTemplateReplica> payloadType() { return MaintenanceTemplateReplica.class; }

    @Override
    public void apply(String key, MaintenanceTemplateReplica payload, Long version) {
        templateRepository.save(payload.toDomain());
    }

    @Override
    public void delete(String key) {
        try {
            templateRepository.findByName(key).ifPresent(templateRepository::delete);
        } catch (DataIntegrityViolationException e) {
            // A maintenance record on this replica still uses the template: keep it rather than block replication.
            log.warn("Maintenance template {} not deleted on this replica: still in use", key);
        }
    }

    @Override
    public List<Item<MaintenanceTemplateReplica>> exportAll() {
        return templateRepository.findAll().stream()
                .map(t -> new Item<>(t.getName(), MaintenanceTemplateReplica.from(t), null))
                .toList();
    }
}
