package aisafe.maintenance.infrastructure.persistence.jpa;

import aisafe.aircrafts.domain.RegistrationNumber;
import aisafe.aircrafts.domain.ModelName;
import aisafe.maintenance.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("jpa")
@Transactional
class MaintenanceRecordJpaRepositoryAdditionalTest {

    @Autowired
    private MaintenanceRecordRepository repository;

    @Autowired
    private SpringDataMaintenancePartRepository partSpringRepo;

    @Test
    void ensureSaveThrowsMaintenancePartNotFoundExceptionWhenPartDoesNotExist() {
        MaintenanceTemplate template = new MaintenanceTemplate(
                "Inspection Check",
                MaintenanceType.INSPECTION,
                List.of(new ModelName("A320")),
                List.of("Check"),
                100,
                10
        );

        MaintenancePart nonExistentPart = new MaintenancePart(
                "P-XYZ", "Part", "Desc", 1, 1, MaintenanceComponent.ENGINE
        );

        MaintenanceRecord record = new MaintenanceRecord(
                UUID.randomUUID(),
                "Desc",
                LocalDateTime.now(),
                5,
                List.of(nonExistentPart),
                "Notes",
                template,
                MaintenanceStatus.PLANNED,
                Set.of(MaintenanceComponent.ENGINE),
                new RegistrationNumber("CS-TPA"),
                BigDecimal.valueOf(100.0),
                null
        );

        assertThrows(MaintenancePartNotFoundException.class, () -> repository.save(record));
    }

    @Test
    void ensureSaveThrowsMaintenanceTemplateNotFoundExceptionWhenTemplateDoesNotExist() {
        MaintenancePartJpaEntity partJpa = new MaintenancePartJpaEntity(
                "P-EXISTENT", "Existent Part", "Desc", 10, 2, MaintenanceComponent.ENGINE
        );
        partSpringRepo.save(partJpa);

        MaintenancePart domainPart = new MaintenancePart(
                "P-EXISTENT",
                "Existent Part",
                "Desc",
                10,
                2,
                MaintenanceComponent.ENGINE
        );

        MaintenanceTemplate nonExistentTemplate = new MaintenanceTemplate(
                "NonExistentTemplate",
                MaintenanceType.INSPECTION,
                List.of(new ModelName("A320")),
                List.of("Check"),
                100,
                10
        );

        MaintenanceRecord record = new MaintenanceRecord(
                UUID.randomUUID(),
                "Desc",
                LocalDateTime.now(),
                5,
                List.of(domainPart),
                "Notes",
                nonExistentTemplate,
                MaintenanceStatus.PLANNED,
                Set.of(MaintenanceComponent.ENGINE),
                new RegistrationNumber("CS-TPA"),
                BigDecimal.valueOf(100.0),
                null
        );

        assertThrows(MaintenanceTemplateNotFoundException.class, () -> repository.save(record));
    }

    @Test
    void ensureMaintenanceRecordJpaEntityGettersSettersAndVersion() {
        UUID recordId = UUID.randomUUID();
        LocalDateTime startDate = LocalDateTime.now();
        RegistrationNumberJpaEmbeddable reg = new RegistrationNumberJpaEmbeddable("CS-TPA");
        BigDecimal cost = BigDecimal.valueOf(500.0);

        MaintenanceRecordJpaEntity entity = new MaintenanceRecordJpaEntity(
                recordId, "Description", startDate, 10, "Notes",
                Collections.emptyList(), null, MaintenanceStatus.PLANNED,
                Collections.emptySet(), reg, cost
        );
        entity.setId(123L);
        entity.setVersion(2L);
        entity.setNotes("New Notes");
        entity.setStatus(MaintenanceStatus.COMPLETED);
        entity.setCompletedAt(startDate.plusDays(1));

        assertEquals(123L, entity.getId());
        assertEquals(2L, entity.getVersion());
        assertEquals(recordId, entity.getRecordId());
        assertEquals("Description", entity.getDescription());
        assertEquals(startDate, entity.getStartDate());
        assertEquals(10, entity.getExpectedDuration());
        assertEquals("New Notes", entity.getNotes());
        assertTrue(entity.getParts().isEmpty());
        assertNull(entity.getTemplate());
        assertEquals(MaintenanceStatus.COMPLETED, entity.getStatus());
        assertTrue(entity.getComponents().isEmpty());
        assertEquals("CS-TPA", entity.getAircraftRegistration().getNumber());
        assertEquals(cost, entity.getCost());
        assertEquals(startDate.plusDays(1), entity.getCompletedAt());
    }

    @Autowired
    private SpringDataMaintenanceRecordRepository springRecordRepo;

}
