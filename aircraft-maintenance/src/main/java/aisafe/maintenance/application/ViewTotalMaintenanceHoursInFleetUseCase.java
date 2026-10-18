package aisafe.maintenance.application;

import aisafe.shared.application.UseCase;
import aisafe.maintenance.application.dtos.ViewTotalMaintenanceHoursInFleetResponse;
import org.springframework.transaction.annotation.Transactional;

/**
 * Views all maintenance hours from the whole fleet by joining all the maintenance record hours
 */
@UseCase(readOnly = true)
@Transactional(readOnly = true)
public class ViewTotalMaintenanceHoursInFleetUseCase {
    private final MaintenanceRecordsAcrossReplicas records;

    public ViewTotalMaintenanceHoursInFleetUseCase(MaintenanceRecordsAcrossReplicas records) {
        this.records = records;
    }

    /**
     * Calculates the total maintenance hours for the entire fleet by summing up the expected duration of all maintenance records
     * of every replica.
     * @return a response containing the total maintenance hours in the fleet
     */
    public ViewTotalMaintenanceHoursInFleetResponse execute() {
        long totalHours = records.findAll().stream()
                .mapToLong(v -> v.value().getExpectedDuration())
                .sum();

        return new ViewTotalMaintenanceHoursInFleetResponse((int) totalHours);
    }
}
