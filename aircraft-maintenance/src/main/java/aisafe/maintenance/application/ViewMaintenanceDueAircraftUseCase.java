package aisafe.maintenance.application;

import aisafe.aircrafts.application.AircraftAcrossReplicas;
import aisafe.flightcopies.application.FlightCopiesAcrossReplicas;
import aisafe.flightcopies.domain.FlightCopy;
import aisafe.flightcopies.domain.FlightCopyStatus;
import aisafe.shared.application.Versioned;
import aisafe.aircrafts.domain.RegistrationNumber;
import aisafe.aircrafts.domain.Aircraft;
import aisafe.aircrafts.domain.ModelName;
import aisafe.maintenance.application.dtos.MaintenanceDueAircraftResponse;
import aisafe.maintenance.domain.MaintenanceRecord;
import aisafe.maintenance.domain.MaintenanceStatus;
import aisafe.maintenance.domain.MaintenanceTemplate;
import aisafe.maintenance.domain.MaintenanceTemplateRepository;
import aisafe.shared.application.UseCase;
import aisafe.shared.domain.PaginatedResult;
import org.springframework.beans.factory.annotation.Value;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.*;
import java.util.ArrayList;
import java.util.List;

@UseCase(readOnly = true)
public class ViewMaintenanceDueAircraftUseCase {

    private final AircraftAcrossReplicas fleet;
    private final MaintenanceRecordsAcrossReplicas records;
    private final MaintenanceTemplateRepository maintenanceTemplateRepository;
    private final FlightCopiesAcrossReplicas flights;

    private final Double defaultFlightHoursThreshold;
    private final Long defaultDaysThreshold;

    public ViewMaintenanceDueAircraftUseCase(
            AircraftAcrossReplicas fleet,
            MaintenanceRecordsAcrossReplicas records,
            MaintenanceTemplateRepository maintenanceTemplateRepository,
            FlightCopiesAcrossReplicas flights,
            @Value("${aisafe.maintenance.threshold.default-flight-hours:300}") Double defaultFlightHoursThreshold,
            @Value("${aisafe.maintenance.threshold.default-days:365}") Long defaultDaysThreshold) {
        this.fleet = fleet;
        this.records = records;
        this.maintenanceTemplateRepository = maintenanceTemplateRepository;
        this.flights = flights;
        this.defaultFlightHoursThreshold = defaultFlightHoursThreshold;
        this.defaultDaysThreshold = defaultDaysThreshold;
    }

    /**
     * Aircraft due for maintenance, over the aircraft, records and flight copies of every replica
     * (gathered once, then joined here).
     */
    public PaginatedResult<MaintenanceDueAircraftResponse> execute(int pageNumber, int pageSize) {
        List<MaintenanceDueAircraftResponse> dueList = new ArrayList<>();
        List<Aircraft> allAircraft = fleet.findAll();
        List<MaintenanceTemplate> allTemplates = maintenanceTemplateRepository.findAll();
        // Completed records of each aircraft, most recently completed first
        Map<RegistrationNumber, List<MaintenanceRecord>> completedByAircraft = records.findAll().stream()
                .map(Versioned::value)
                .filter(r -> r.getStatus() == MaintenanceStatus.COMPLETED)
                .sorted(Comparator.comparing(MaintenanceRecord::getCompletedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.groupingBy(MaintenanceRecord::getAircraftRegistration));
        Map<RegistrationNumber, List<FlightCopy>> completedFlightsByAircraft = flights.findAll().stream()
                .filter(f -> f.getStatus() == FlightCopyStatus.COMPLETED)
                .collect(Collectors.groupingBy(FlightCopy::getAircraftRegistrationNumber));
        LocalDateTime now = LocalDateTime.now();

        for (Aircraft aircraft : allAircraft) {
            String modelName = aircraft.getModel().getModelName();

            // Find templates applicable to this model
            List<MaintenanceTemplate> applicableTemplates = allTemplates.stream()
                    .filter(t -> t.getApplicableModelNames().contains(new ModelName(modelName)))
                    .toList();

            List<MaintenanceRecord> completedRecords = completedByAircraft.getOrDefault(aircraft.getRegistrationNumber(), List.of());
            List<FlightCopy> completedFlights = completedFlightsByAircraft.getOrDefault(aircraft.getRegistrationNumber(), List.of());

            if (!applicableTemplates.isEmpty()) {
                for (MaintenanceTemplate template : applicableTemplates) {
                    // Find last completed record for this template
                    MaintenanceRecord lastCompleted = completedRecords.stream()
                            .filter(r -> r.getTemplate().getName().equals(template.getName()))
                            .findFirst() // completedRecords is sorted by completedAt desc
                            .orElse(null);

                    LocalDateTime startCalDate;
                    OffsetDateTime startFlightDate;

                    if (lastCompleted != null && lastCompleted.getCompletedAt() != null) {
                        startCalDate = lastCompleted.getCompletedAt();
                        startFlightDate = startCalDate.atOffset(ZoneOffset.UTC);
                    } else {
                        startCalDate = aircraft.getManufacturingDate().atStartOfDay();
                        startFlightDate = startCalDate.atOffset(ZoneOffset.UTC);
                    }

                    long elapsedDays = Duration.between(startCalDate, now).toDays();
                    Double flightHours = hoursSince(completedFlights, startFlightDate);

                    boolean due = false;
                    String reason = "";
                    if (flightHours >= template.getIntervalFlightHours()) {
                        due = true;
                        reason += String.format("Exceeded flight hours limit (%.1f/%.1f hrs). ", flightHours, (double) template.getIntervalFlightHours());
                    }
                    if (elapsedDays >= template.getIntervalDays()) {
                        due = true;
                        reason += String.format("Exceeded elapsed days limit (%d/%d days). ", elapsedDays, template.getIntervalDays());
                    }

                    if (due) {
                        dueList.add(new MaintenanceDueAircraftResponse(
                                aircraft.getRegistrationNumber().getNumber(),
                                modelName,
                                reason.trim(),
                                flightHours,
                                elapsedDays,
                                template.getName()
                        ));
                    }
                }
            } else {
                // Fallback to global thresholds if no templates apply
                LocalDateTime startCalDate;
                OffsetDateTime startFlightDate;

                MaintenanceRecord lastCompleted = completedRecords.isEmpty() ? null : completedRecords.get(0);
                if (lastCompleted != null && lastCompleted.getCompletedAt() != null) {
                    startCalDate = lastCompleted.getCompletedAt();
                    startFlightDate = startCalDate.atOffset(ZoneOffset.UTC);
                } else {
                    startCalDate = aircraft.getManufacturingDate().atStartOfDay();
                    startFlightDate = startCalDate.atOffset(ZoneOffset.UTC);
                }

                long elapsedDays = Duration.between(startCalDate, now).toDays();
                Double flightHours = hoursSince(completedFlights, startFlightDate);

                boolean due = false;
                String reason = "";
                if (flightHours >= defaultFlightHoursThreshold) {
                    due = true;
                    reason += String.format("Exceeded default flight hours limit (%.1f/%.1f hrs). ", flightHours, defaultFlightHoursThreshold);
                }
                if (elapsedDays >= defaultDaysThreshold) {
                    due = true;
                    reason += String.format("Exceeded default elapsed days limit (%d/%d days). ", elapsedDays, defaultDaysThreshold);
                }

                if (due) {
                    dueList.add(new MaintenanceDueAircraftResponse(
                            aircraft.getRegistrationNumber().getNumber(),
                            modelName,
                            reason.trim(),
                            flightHours,
                            elapsedDays,
                            "Default Fallback"
                    ));
                }
            }
        }
        int totalElements = dueList.size();
        int start = pageNumber * pageSize;
        if (start >= totalElements) {
            return new PaginatedResult<>(List.of(), totalElements);
        }
        int end = Math.min(start + pageSize, totalElements);
        List<MaintenanceDueAircraftResponse> paginatedData = dueList.subList(start, end);
        return new PaginatedResult<>(paginatedData, totalElements);
    }

    /** Hours flown in the completed flights that departed at or after {@code since}. */
    private static double hoursSince(List<FlightCopy> completedFlights, OffsetDateTime since) {
        long seconds = completedFlights.stream()
                .filter(f -> !f.getDepartureDateTime().isBefore(since))
                .mapToLong(f -> f.getDuration().toSeconds())
                .sum();
        return seconds / 3600.0;
    }
}
