package aisafe.maintenance.application;

import aisafe.shared.application.Versioned;
import aisafe.maintenance.application.dtos.MaintenanceRecordResponse;
import aisafe.maintenance.domain.MaintenanceRecord;
import aisafe.maintenance.domain.MaintenanceStatus;
import aisafe.shared.application.UseCase;
import aisafe.shared.domain.PaginatedResult;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@UseCase(readOnly = true)
public class ViewOngoingMaintenanceUseCase {

    private final MaintenanceRecordsAcrossReplicas records;

    public ViewOngoingMaintenanceUseCase(MaintenanceRecordsAcrossReplicas records) {
        this.records = records;
    }

    /** Records in progress on every replica, most recent start first. */
    public PaginatedResult<MaintenanceRecordResponse> execute(int pageNumber, int pageSize) {
        List<MaintenanceRecordResponse> ongoing = records.findAll().stream()
                .filter(v -> v.value().getStatus() == MaintenanceStatus.IN_PROGRESS)
                .sorted(Comparator.comparing((Versioned<MaintenanceRecord> v) -> v.value().getStartDate()).reversed()
                        .thenComparing(v -> v.value().getRecordId()))
                .map(ViewOngoingMaintenanceUseCase::toResponse)
                .toList();

        return PaginatedResult.pageOf(ongoing, pageNumber, pageSize);
    }

    private static MaintenanceRecordResponse toResponse(Versioned<MaintenanceRecord> versioned) {
        MaintenanceRecord r = versioned.value();
        return new MaintenanceRecordResponse(
                r.getRecordId(),
                r.getDescription(),
                r.getStartDate(),
                r.getExpectedDuration(),
                r.getNotes(),
                r.getParts().stream().map(p -> p.getPartNumber()).toList(),
                r.getTemplate().getName(),
                r.getStatus().name(),
                r.getAircraftRegistration().getNumber(),
                versioned.version(),
                r.getComponents().stream().map(Enum::name).collect(Collectors.toSet()),
                r.getCost()
        );
    }
}
