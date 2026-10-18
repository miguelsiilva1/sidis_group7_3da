package aisafe.maintenance.infrastructure.replication;

import aisafe.maintenance.domain.MaintenanceComponent;
import aisafe.maintenance.domain.MaintenancePart;

/**
 * A maintenance part as copied to the other replicas (catalog: every replica holds every part).
 */
public record MaintenancePartReplica(
        String partNumber,
        String name,
        String description,
        Integer stockQuantity,
        Integer minimumThreshold,
        MaintenanceComponent component
) {
    public static MaintenancePartReplica from(MaintenancePart part) {
        return new MaintenancePartReplica(part.getPartNumber(), part.getName(), part.getDescription(),
                part.getStockQuantity(), part.getMinimumThreshold(), part.getComponent());
    }

    public MaintenancePart toDomain() {
        return new MaintenancePart(partNumber, name, description, stockQuantity, minimumThreshold, component);
    }
}
