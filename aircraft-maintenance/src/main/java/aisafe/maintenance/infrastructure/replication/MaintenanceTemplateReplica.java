package aisafe.maintenance.infrastructure.replication;

import aisafe.aircrafts.domain.ModelName;
import aisafe.maintenance.domain.MaintenanceTemplate;
import aisafe.maintenance.domain.MaintenanceType;

import java.util.List;

/**
 * A maintenance template as copied to the other replicas (catalog: every replica holds every template).
 */
public record MaintenanceTemplateReplica(
        String name,
        MaintenanceType templateType,
        List<String> applicableModelNames,
        List<String> checklist,
        Integer intervalFlightHours,
        Integer intervalDays
) {
    public static MaintenanceTemplateReplica from(MaintenanceTemplate template) {
        return new MaintenanceTemplateReplica(template.getName(), template.getTemplateType(),
                template.getApplicableModelNames().stream().map(ModelName::getName).toList(),
                template.getChecklist(), template.getIntervalFlightHours(), template.getIntervalDays());
    }

    public MaintenanceTemplate toDomain() {
        return new MaintenanceTemplate(name, templateType, applicableModelNames.stream().map(ModelName::new).toList(),
                checklist, intervalFlightHours, intervalDays);
    }
}
