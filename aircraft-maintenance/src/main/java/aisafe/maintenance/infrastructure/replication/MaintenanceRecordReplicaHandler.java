package aisafe.maintenance.infrastructure.replication;

import aisafe.maintenance.domain.MaintenancePart;
import aisafe.maintenance.domain.MaintenancePartNotFoundException;
import aisafe.maintenance.domain.MaintenancePartRepository;
import aisafe.maintenance.domain.MaintenanceRecordRepository;
import aisafe.maintenance.domain.MaintenanceTemplate;
import aisafe.maintenance.domain.MaintenanceTemplateNotFoundException;
import aisafe.maintenance.domain.MaintenanceTemplateRepository;
import aisafe.maintenance.infrastructure.persistence.jpa.SpringDataMaintenanceRecordRepository;
import aisafe.shared.infrastructure.replication.ReplicaHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Applies maintenance record replicas (record). Which change wins is decided before, by its
 * stamp ({@code ReplicaApplier}); after applying, the local version is set to the sender's.
 */
@Component
public class MaintenanceRecordReplicaHandler implements ReplicaHandler<MaintenanceRecordReplica> {

    public static final String TYPE = "maintenance-records";

    private final MaintenanceRecordRepository recordRepository;
    private final MaintenancePartRepository partRepository;
    private final MaintenanceTemplateRepository templateRepository;
    private final SpringDataMaintenanceRecordRepository springRepo;

    public MaintenanceRecordReplicaHandler(MaintenanceRecordRepository recordRepository,
                                           MaintenancePartRepository partRepository,
                                           MaintenanceTemplateRepository templateRepository,
                                           SpringDataMaintenanceRecordRepository springRepo) {
        this.recordRepository = recordRepository;
        this.partRepository = partRepository;
        this.templateRepository = templateRepository;
        this.springRepo = springRepo;
    }

    @Override public String type() { return TYPE; }
    @Override public int order() { return 50; }
    @Override public boolean isCatalog() { return false; }
    @Override public Class<MaintenanceRecordReplica> payloadType() { return MaintenanceRecordReplica.class; }

    @Override
    public void apply(String key, MaintenanceRecordReplica payload, Long version) {
        UUID recordId = UUID.fromString(key);
        List<MaintenancePart> parts = payload.partNumbers().stream()
                .map(pn -> partRepository.findByPartNumber(pn)
                        .orElseThrow(() -> new MaintenancePartNotFoundException("Part not found: " + pn)))
                .toList();
        MaintenanceTemplate template = templateRepository.findByName(payload.templateName())
                .orElseThrow(() -> new MaintenanceTemplateNotFoundException("Template not found: " + payload.templateName()));
        recordRepository.save(payload.toDomain(parts, template));
        if (version != null) {
            springRepo.alignVersion(recordId, version);
        }
    }

    @Override
    public void delete(String key) {
        recordRepository.findByRecordId(UUID.fromString(key)).ifPresent(recordRepository::delete);
    }

    @Override
    public List<Item<MaintenanceRecordReplica>> exportAll() {
        return recordRepository.findAll().stream()
                .map(r -> new Item<>(r.getRecordId().toString(), MaintenanceRecordReplica.from(r),
                        recordRepository.findVersionFor(r.getRecordId())))
                .toList();
    }
}
