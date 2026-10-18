package aisafe.maintenance.infrastructure.replication;

import aisafe.aircrafts.domain.RegistrationNumber;
import aisafe.maintenance.domain.MaintenanceComponent;
import aisafe.maintenance.domain.MaintenancePart;
import aisafe.maintenance.domain.MaintenanceRecord;
import aisafe.maintenance.domain.MaintenanceStatus;
import aisafe.maintenance.domain.MaintenanceTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A maintenance record as copied to the other holder (record: held by its home replica and the
 * next one). The template and parts are referenced by name: they are catalogs, present on every
 * replica. The aircraft is referenced by its registration only.
 */
public record MaintenanceRecordReplica(
        UUID recordId,
        String description,
        LocalDateTime startDate,
        Integer expectedDuration,
        String notes,
        List<String> partNumbers,
        String templateName,
        MaintenanceStatus status,
        Set<MaintenanceComponent> components,
        String aircraftRegistration,
        BigDecimal cost,
        LocalDateTime completedAt
) {
    public static MaintenanceRecordReplica from(MaintenanceRecord record) {
        return new MaintenanceRecordReplica(record.getRecordId(), record.getDescription(), record.getStartDate(),
                record.getExpectedDuration(), record.getNotes(),
                record.getParts().stream().map(MaintenancePart::getPartNumber).toList(),
                record.getTemplate().getName(), record.getStatus(), record.getComponents(),
                record.getAircraftRegistration().getNumber(), record.getCost(), record.getCompletedAt());
    }

    public MaintenanceRecord toDomain(List<MaintenancePart> parts, MaintenanceTemplate template) {
        return new MaintenanceRecord(recordId, description, startDate, expectedDuration, parts, notes, template,
                status, components, new RegistrationNumber(aircraftRegistration), cost, completedAt);
    }
}
