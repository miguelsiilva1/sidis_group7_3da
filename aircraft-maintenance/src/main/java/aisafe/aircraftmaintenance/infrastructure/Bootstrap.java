package aisafe.aircraftmaintenance.infrastructure;

import aisafe.aircrafts.domain.*;
import aisafe.maintenance.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Populates the database with the aircraft and maintenance sample data of the monolith on startup.
 *
 * <p>Data is partitioned between replicas (each record lives on the replica that created it), so
 * only one replica loads the sample data: the one named in {@code aisafe.bootstrap.seed-node}.
 * When that property is empty (local development), the data is always loaded.</p>
 */
@Component
public class Bootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(Bootstrap.class);

    private final AircraftModelRepository aircraftModelRepository;
    private final AircraftRepository aircraftRepository;
    private final MaintenancePartRepository maintenancePartRepository;
    private final MaintenanceTemplateRepository maintenanceTemplateRepository;
    private final MaintenanceRecordRepository maintenanceRecordRepository;
    private final String nodeId;
    private final String seedNode;

    public Bootstrap(AircraftModelRepository aircraftModelRepository,
            AircraftRepository aircraftRepository,
            MaintenancePartRepository maintenancePartRepository,
            MaintenanceTemplateRepository maintenanceTemplateRepository,
            MaintenanceRecordRepository maintenanceRecordRepository,
            @Value("${aisafe.node.id}") String nodeId,
            @Value("${aisafe.bootstrap.seed-node:}") String seedNode) {
        this.aircraftModelRepository = aircraftModelRepository;
        this.aircraftRepository = aircraftRepository;
        this.maintenancePartRepository = maintenancePartRepository;
        this.maintenanceTemplateRepository = maintenanceTemplateRepository;
        this.maintenanceRecordRepository = maintenanceRecordRepository;
        this.nodeId = nodeId;
        this.seedNode = seedNode;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedNode.isBlank() && !seedNode.equals(nodeId)) {
            log.info("Replica {} does not load the sample data (seed node: {})", nodeId, seedNode);
            return;
        }

        // bootstrap for aircraft package
        if (aircraftModelRepository.count() == 0) {
            aircraftModelRepository.save(new AircraftModel("Airbus A320neo", Manufacturer.AIRBUS, 24210.0, 6570.0,
                    828.0, null, 240));
            aircraftModelRepository.save(new AircraftModel("Boeing 737 MAX", Manufacturer.BOEING, 25941.0, 6570.0,
                    839.0, null, 300));
            aircraftModelRepository.save(new AircraftModel("Embraer E195-E2", Manufacturer.EMBRAER, 13000.0, 4260.0,
                    829.0, null, 146));
        }
        if (aircraftRepository.count() == 0) {
            AircraftModel a320neo = aircraftModelRepository.findByModelName("Airbus A320neo").orElseThrow();
            AircraftModel b737max = aircraftModelRepository.findByModelName("Boeing 737 MAX").orElseThrow();
            AircraftModel e195e2 = aircraftModelRepository.findByModelName("Embraer E195-E2").orElseThrow();

            aircraftRepository.save(new Aircraft(AircraftStatus.AVAILABLE, LocalDate.parse("2019-05-25"), a320neo,
                    new RegistrationNumber("CS-TKA"), 180, a320neo.getMaxRange(), List.of("WiFi", "In-flight entertainment")));
            aircraftRepository.save(new Aircraft(AircraftStatus.UNDER_MAINTENANCE, LocalDate.parse("2020-08-15"),
                    b737max, new RegistrationNumber("CS-TKB"), 200, b737max.getMaxRange(),
                    List.of("WiFi", "In-flight entertainment", "Extra legroom")));
            aircraftRepository.save(new Aircraft(AircraftStatus.IN_FLIGHT, LocalDate.parse("2018-03-10"), e195e2,
                    new RegistrationNumber("CS-TKC"), 120, e195e2.getMaxRange(), List.of("WiFi")));
        }


        // bootstrap for maintenance package
        if (maintenanceTemplateRepository.count() == 0) {
            AircraftModel a320neo = aircraftModelRepository.findByModelName("Airbus A320neo").orElseThrow();
            AircraftModel b737max = aircraftModelRepository.findByModelName("Boeing 737 MAX").orElseThrow();
            AircraftModel e195e2 = aircraftModelRepository.findByModelName("Embraer E195-E2").orElseThrow();
            List<AircraftModel> models = List.of(a320neo, b737max, e195e2);
            maintenanceTemplateRepository.save(new MaintenanceTemplate("Inspection to the starter motor",
                    MaintenanceType.INSPECTION, List.of(new ModelName("Airbus A320neo"), new ModelName("Boeing 737 MAX")), List.of("starterMotor inspection"), 300, 365));
        }

        if (maintenancePartRepository.count() == 0) {
            maintenancePartRepository.save(new MaintenancePart("ST-1001", "Starter Motor",
                    "Motor responsible for starting the aircraft's engines.", 300, 15, MaintenanceComponent.ENGINE));
        }

        if (maintenanceRecordRepository.count() == 0) {
            MaintenancePart part = maintenancePartRepository.findByPartNumber("ST-1001").orElseThrow();
            MaintenanceTemplate template = maintenanceTemplateRepository.findByName("Inspection to the starter motor")
                    .orElseThrow();
            Aircraft aircraft1 = aircraftRepository.findByRegistrationNumber(new RegistrationNumber("CS-TKA"))
                    .orElseThrow();
            Aircraft aircraft2 = aircraftRepository.findByRegistrationNumber(new RegistrationNumber("CS-TKB"))
                    .orElseThrow();
            maintenanceRecordRepository.save(new MaintenanceRecord(UUID.randomUUID(), "Simple inspection to the starter motor",
                    LocalDateTime.parse("2024-06-01T10:00:00"), 120, List.of(part), "No issues found during the inspection.",
                    template, MaintenanceStatus.PLANNED, Set.of(MaintenanceComponent.ENGINE), aircraft1.getRegistrationNumber(),
                    java.math.BigDecimal.valueOf(1500.00), null));
            maintenanceRecordRepository.save(new MaintenanceRecord(UUID.randomUUID(), "Detailed inspection to the starter motor",
                    LocalDateTime.parse("2024-06-10T14:00:00"), 240, List.of(part),
                    "Minor wear detected, replacement recommended within the next 6 months.", template,
                    MaintenanceStatus.PLANNED, Set.of(MaintenanceComponent.ENGINE), aircraft2.getRegistrationNumber(),
                    java.math.BigDecimal.valueOf(2750.00), null));
            maintenanceRecordRepository.save(new MaintenanceRecord(UUID.randomUUID(), "Completed starter motor overhaul",
                    LocalDateTime.parse("2024-07-01T08:00:00"), 32, List.of(part), "Overhaul completed successfully.",
                    template, MaintenanceStatus.COMPLETED, Set.of(MaintenanceComponent.ENGINE), aircraft1.getRegistrationNumber(),
                    java.math.BigDecimal.valueOf(3200.00), LocalDateTime.parse("2024-07-02T16:00:00")));
        }
    }
}
