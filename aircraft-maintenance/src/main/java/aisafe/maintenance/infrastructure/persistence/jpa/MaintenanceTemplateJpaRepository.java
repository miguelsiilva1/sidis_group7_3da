package aisafe.maintenance.infrastructure.persistence.jpa;

import aisafe.shared.infrastructure.replication.Replicator;
import aisafe.maintenance.infrastructure.replication.MaintenanceTemplateReplicaHandler;
import aisafe.maintenance.infrastructure.replication.MaintenanceTemplateReplica;
import aisafe.maintenance.domain.MaintenanceTemplate;
import aisafe.maintenance.domain.MaintenanceTemplateNotFoundException;
import aisafe.maintenance.domain.MaintenanceTemplateRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@Profile("jpa")
public class MaintenanceTemplateJpaRepository implements MaintenanceTemplateRepository {

    private final SpringDataMaintenanceTemplateRepository springRepo;
    private final Replicator replicator;

    public MaintenanceTemplateJpaRepository(SpringDataMaintenanceTemplateRepository springRepo, Replicator replicator) {
        this.springRepo = springRepo;
        this.replicator = replicator;
    }

    @Override
    public long count() {
        return springRepo.count();
    }

    @Override
    public List<MaintenanceTemplate> findAll() {
        return springRepo.findAll().stream()
                .map(MaintenanceTemplateMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsByName(String name) {
        return springRepo.existsByName(name);
    }

    @Override
    public Optional<MaintenanceTemplate> findByName(String name) {
        return springRepo.findByName(name).map(MaintenanceTemplateMapper::toDomain);
    }

    @Override
    public MaintenanceTemplate save(MaintenanceTemplate template) {
        MaintenanceTemplateJpaEntity existing = springRepo.findByName(template.getName()).orElse(null);
        MaintenanceTemplateJpaEntity jpaEntity = MaintenanceTemplateMapper.toJpa(template);
        if (existing != null) {
            jpaEntity.setId(existing.getId());
        }
        springRepo.save(jpaEntity);
        // Catalog: copied to every replica (decision R1)
        replicator.catalogSaved(MaintenanceTemplateReplicaHandler.TYPE, template.getName(), MaintenanceTemplateReplica.from(template));
        return template;
    }

    @Override
    public void delete(MaintenanceTemplate template) {
        MaintenanceTemplateJpaEntity jpaEntity = springRepo.findByName(template.getName())
                .orElseThrow(() -> new MaintenanceTemplateNotFoundException("Template not found: " + template.getName()));
        springRepo.delete(jpaEntity);
        replicator.catalogDeleted(MaintenanceTemplateReplicaHandler.TYPE, template.getName());
    }
}
