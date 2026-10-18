package aisafe.maintenance.application;

import aisafe.shared.application.Versioned;
import aisafe.aircrafts.application.AircraftAcrossReplicas;
import aisafe.shared.application.UseCase;
import aisafe.aircrafts.domain.AircraftNotFoundException;
import aisafe.aircrafts.domain.RegistrationNumber;
import aisafe.maintenance.application.dtos.ViewAllMaintenanceRecordsResponse;
import aisafe.maintenance.domain.MaintenanceRecord;
import aisafe.shared.domain.PaginatedResult;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Views all maintenance records from a specific aircraft using its registration number
 */
@UseCase(readOnly = true)
public class ViewAllMaintenanceRecordsUseCase {
    private final MaintenanceRecordsAcrossReplicas records;
    private final AircraftAcrossReplicas aircraft;

    public ViewAllMaintenanceRecordsUseCase(MaintenanceRecordsAcrossReplicas records, AircraftAcrossReplicas aircraft) {
        this.records = records;
        this.aircraft = aircraft;
    }

    /** The aircraft's records from every replica, by start date. */
    public PaginatedResult<ViewAllMaintenanceRecordsResponse> execute(RegistrationNumber registrationNumber, int pageNumber, int pageSize) {
        if (aircraft.findAll().stream().noneMatch(a -> a.getRegistrationNumber().equals(registrationNumber))) {
            throw new AircraftNotFoundException("Aircraft with registration number: " + registrationNumber.getNumber() + " not found.");
        }

        List<ViewAllMaintenanceRecordsResponse> data = records.findAll().stream()
                .map(Versioned::value)
                .filter(r -> r.getAircraftRegistration().equals(registrationNumber))
                .sorted(Comparator.comparing(MaintenanceRecord::getStartDate).thenComparing(MaintenanceRecord::getRecordId))
                .map(this::toResponse)
                .toList();

        return PaginatedResult.pageOf(data, pageNumber, pageSize);
    }

    private ViewAllMaintenanceRecordsResponse toResponse(MaintenanceRecord record) {
        return new ViewAllMaintenanceRecordsResponse(
                record.getParts().stream().map(p -> p.getPartNumber()).toList(),
                record.getTemplate().getName(),
                record.getStartDate(),
                record.getExpectedDuration(),
                record.getStatus(),
                record.getNotes(),
                record.getAircraftRegistration().getNumber(),
                record.getComponents().stream().map(Enum::name).collect(Collectors.toSet())
        );
    }
}
