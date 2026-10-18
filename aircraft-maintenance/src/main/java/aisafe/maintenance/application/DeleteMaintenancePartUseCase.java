package aisafe.maintenance.application;

import aisafe.shared.application.UseCase;
import aisafe.maintenance.domain.MaintenancePart;
import aisafe.maintenance.domain.MaintenancePartNotFoundException;
import aisafe.maintenance.domain.MaintenancePartRepository;
import aisafe.shared.domain.ResourceInUseException;

@UseCase
public class DeleteMaintenancePartUseCase {
    private final MaintenancePartRepository maintenancePartRepository;
    private final MaintenanceRecordsAcrossReplicas records;

    public DeleteMaintenancePartUseCase(MaintenancePartRepository maintenancePartRepository,
                                        MaintenanceRecordsAcrossReplicas records) {
        this.maintenancePartRepository = maintenancePartRepository;
        this.records = records;
    }

    public void execute(String partNumber) {
        MaintenancePart part = maintenancePartRepository.findByPartNumber(partNumber)
                .orElseThrow(() -> new MaintenancePartNotFoundException("Maintenance part not found with part number: " + partNumber));
        // Records are partitioned: a record of another replica may use the part
        boolean inUse = records.findAll().stream()
                .anyMatch(v -> v.value().getParts().stream().anyMatch(p -> p.getPartNumber().equals(part.getPartNumber())));
        if (inUse) {
            throw new ResourceInUseException("Maintenance part is referenced by existing maintenance records and cannot be deleted.");
        }
        maintenancePartRepository.delete(part);
    }
}
