package aisafe.maintenance.infrastructure.persistence.jpa;

import aisafe.maintenance.domain.MaintenanceComponent;
import aisafe.maintenance.domain.MaintenanceStatus;
import aisafe.maintenance.domain.MaintenanceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("jpa")
class MaintenanceRecordRepositoryTest {

    @Autowired
    private SpringDataMaintenanceRecordRepository recordRepository;

    @Autowired
    private SpringDataMaintenancePartRepository partRepository;

    @Autowired
    private SpringDataMaintenanceTemplateRepository templateRepository;

    private MaintenancePartJpaEntity part;
    private MaintenanceTemplateJpaEntity template;

    @BeforeEach
    void setUp() {
        part = new MaintenancePartJpaEntity("PN-001", "Filter", "A filter", 10, 2, MaintenanceComponent.ENGINE);
        partRepository.save(part);

        template = new MaintenanceTemplateJpaEntity("Annual", MaintenanceType.INSPECTION, List.of("A320"), List.of("Check"), 500, 365);
        templateRepository.save(template);
    }

    @Test
    void ensureRecordCanBeSavedAndFoundByRecordId() {
        UUID recordId = UUID.randomUUID();
        MaintenanceRecordJpaEntity record = new MaintenanceRecordJpaEntity(
                recordId, "Oil change", LocalDateTime.now(), 4, "notes",
                List.of(part), template, MaintenanceStatus.PLANNED, Set.of(MaintenanceComponent.ENGINE),
                new RegistrationNumberJpaEmbeddable("CS-TPA"), BigDecimal.valueOf(100));
        recordRepository.save(record);

        var found = recordRepository.findByRecordId(recordId);
        assertTrue(found.isPresent());
        assertEquals("Oil change", found.get().getDescription());
        assertEquals("CS-TPA", found.get().getAircraftRegistration().getNumber());
    }

}
