package aisafe.maintenance.application;

import aisafe.shared.application.UseCase;
import aisafe.maintenance.domain.MaintenanceTemplate;
import aisafe.maintenance.domain.MaintenanceTemplateNotFoundException;
import aisafe.maintenance.domain.MaintenanceTemplateRepository;
import aisafe.shared.domain.ResourceInUseException;

@UseCase
public class DeleteMaintenanceTemplateUseCase {
    private final MaintenanceTemplateRepository maintenanceTemplateRepository;
    private final MaintenanceRecordsAcrossReplicas records;

    public DeleteMaintenanceTemplateUseCase(MaintenanceTemplateRepository maintenanceTemplateRepository,
                                            MaintenanceRecordsAcrossReplicas records) {
        this.maintenanceTemplateRepository = maintenanceTemplateRepository;
        this.records = records;
    }

    public void execute(String name) {
        MaintenanceTemplate template = maintenanceTemplateRepository.findByName(name)
                .orElseThrow(() -> new MaintenanceTemplateNotFoundException("Maintenance template not found with name: " + name));
        // Records are partitioned: a record of another replica may use the template
        boolean inUse = records.findAll().stream()
                .anyMatch(v -> v.value().getTemplate().getName().equals(template.getName()));
        if (inUse) {
            throw new ResourceInUseException("Maintenance template is referenced by existing maintenance records and cannot be deleted.");
        }
        maintenanceTemplateRepository.delete(template);
    }
}
