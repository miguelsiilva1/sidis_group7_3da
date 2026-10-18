package aisafe.flightcopies.infrastructure.persistence.jpa;

import aisafe.flightcopies.domain.FlightCopy;
import aisafe.flightcopies.domain.FlightCopyRepository;
import aisafe.flightcopies.domain.FlightCopyStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The aircraft registrations are plain text here: aircraft belong to aircraft-maintenance.
 */
@SpringBootTest
@ActiveProfiles("jpa")
@Transactional
class FlightCopyJpaRepositoryTest {

    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 9, 1, 8, 0, 0, 0, ZoneOffset.UTC);
    private static final String TKA = "CS-TKA";
    private static final String TKB = "CS-TKB";

    @Autowired
    private FlightCopyRepository repository;

    private static FlightCopy copy(String id, String aircraft, OffsetDateTime departure, int hours, FlightCopyStatus status) {
        return new FlightCopy(id, departure, departure.plusHours(hours), status, "LIS", "OPO", aircraft, 1);
    }

    @BeforeEach
    void setUp() {
        repository.save(copy("a-1", TKA, T0, 2, FlightCopyStatus.COMPLETED));
        repository.save(copy("a-2", TKA, T0.plusDays(10), 3, FlightCopyStatus.COMPLETED));
        repository.save(copy("a-3", TKA, T0.plusDays(20), 5, FlightCopyStatus.SCHEDULED));
        repository.save(copy("b-1", TKB, T0.plusDays(5), 4, FlightCopyStatus.COMPLETED));
    }

    @Test
    void ensureCopiesAreStoredAndFound() {
        assertEquals(4, repository.count());
        FlightCopy found = repository.findById("a-2").orElseThrow();
        assertEquals(FlightCopyStatus.COMPLETED, found.getStatus());
        assertEquals(TKA, found.getAircraftRegistration());
        assertTrue(repository.findById("missing").isEmpty());
    }

    @Test
    void ensureNewerCopyReplacesStoredOneAndDeleteRemovesIt() {
        FlightCopy updated = new FlightCopy("a-3", T0.plusDays(20), T0.plusDays(20).plusHours(5),
                FlightCopyStatus.COMPLETED, "LIS", "OPO", TKA, 2);
        repository.save(updated);
        assertEquals(2, repository.findById("a-3").orElseThrow().getVersion());

        repository.delete(updated);
        assertTrue(repository.findById("a-3").isEmpty());
        assertEquals(3, repository.findAll().size());
    }
}
