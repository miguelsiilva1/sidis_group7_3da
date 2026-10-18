package aisafe.maintenance.application;

import aisafe.aircrafts.domain.*;
import aisafe.maintenance.domain.*;
import aisafe.shared.application.Versioned;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Builds the aircraft and records that the "across replicas" ports return in the use case tests.
 */
final class RecordsFixture {

    static final MaintenancePart PART = new MaintenancePart("P001", "Part1", "Desc1", 10, 2, MaintenanceComponent.ENGINE);

    private RecordsFixture() {
    }

    static Aircraft aircraft(String registration, String modelName) {
        AircraftModel model = new AircraftModel(modelName, Manufacturer.AIRBUS, 26730.0, 6150.0, 833.0, null, 180);
        return new Aircraft(AircraftStatus.AVAILABLE, LocalDate.of(2020, 1, 1), model, new RegistrationNumber(registration), 150, 5000.0, List.of());
    }

    static MaintenanceTemplate template(String name) {
        return new MaintenanceTemplate(name, MaintenanceType.INSPECTION, List.of(new ModelName("A320")), List.of("Check"), 100, 30);
    }

    /** A planned record of the aircraft, version 0. */
    static Versioned<MaintenanceRecord> record(String registration, LocalDateTime start) {
        return record(registration, start, MaintenanceStatus.PLANNED, null, BigDecimal.TEN, 4, MaintenanceComponent.ENGINE);
    }

    static Versioned<MaintenanceRecord> record(String registration, LocalDateTime start, MaintenanceStatus status,
                                               LocalDateTime completedAt, BigDecimal cost, int hours,
                                               MaintenanceComponent component) {
        return record(registration, "Template1", start, status, completedAt, cost, hours, component);
    }

    static Versioned<MaintenanceRecord> record(String registration, String templateName, LocalDateTime start,
                                               MaintenanceStatus status, LocalDateTime completedAt, BigDecimal cost,
                                               int hours, MaintenanceComponent component) {
        return new Versioned<>(new MaintenanceRecord(UUID.randomUUID(), "Maintenance", start, hours, List.of(PART), "notes",
                template(templateName), status, Set.of(component), new RegistrationNumber(registration), cost, completedAt), 0L);
    }
}
