package aisafe.maintenance.application;

import aisafe.shared.application.Versioned;
import aisafe.aircrafts.domain.RegistrationNumber;
import aisafe.maintenance.application.dtos.MaintenanceRecordResponse;
import aisafe.maintenance.domain.MaintenanceComponent;
import aisafe.maintenance.domain.MaintenanceRecord;
import aisafe.shared.application.UseCase;
import aisafe.shared.domain.PaginatedResult;

import java.util.Comparator;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@UseCase(readOnly = true)
public class SearchMaintenanceRecordsUseCase {

    private final MaintenanceRecordsAcrossReplicas records;

    public SearchMaintenanceRecordsUseCase(MaintenanceRecordsAcrossReplicas records) {
        this.records = records;
    }

    /**
     * Searches the records of every replica. Every filter is optional; the page is cut only after
     * the matches of every replica are together, sorted by start date.
     */
    public PaginatedResult<MaintenanceRecordResponse> execute(
            RegistrationNumber aircraftRegistration,
            LocalDateTime from,
            LocalDateTime to,
            MaintenanceComponent component,
            int pageNumber,
            int pageSize) {

        List<MaintenanceRecordResponse> matches = records.findAll().stream()
                .filter(v -> aircraftRegistration == null || v.value().getAircraftRegistration().equals(aircraftRegistration))
                .filter(v -> from == null || !v.value().getStartDate().isBefore(from))
                .filter(v -> to == null || !v.value().getStartDate().isAfter(to))
                .filter(v -> component == null || v.value().getComponents().contains(component))
                .sorted(BY_START_DATE)
                .map(SearchMaintenanceRecordsUseCase::toResponse)
                .toList();

        return PaginatedResult.pageOf(matches, pageNumber, pageSize);
    }

    private static final Comparator<Versioned<MaintenanceRecord>> BY_START_DATE =
            Comparator.comparing((Versioned<MaintenanceRecord> v) -> v.value().getStartDate())
                    .thenComparing(v -> v.value().getRecordId());

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
