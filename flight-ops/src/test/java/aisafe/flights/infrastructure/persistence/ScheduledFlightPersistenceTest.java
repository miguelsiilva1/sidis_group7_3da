package aisafe.flights.infrastructure.persistence;

import aisafe.flights.domain.FlightStatus;
import aisafe.flights.infrastructure.persistence.jpa.ScheduledFlightJpaEntity;
import aisafe.flights.infrastructure.persistence.jpa.SpringDataScheduledFlightRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("jpa")
class ScheduledFlightPersistenceTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SpringDataScheduledFlightRepository repository;

    @Test
    void ensureFindByAircraftRegistrationAndDepartureDateTimeWorks() {
        OffsetDateTime departure = OffsetDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusHours(2);
        OffsetDateTime arrival = departure.plusHours(2);

        ScheduledFlightJpaEntity flight = new ScheduledFlightJpaEntity(
                departure, arrival, FlightStatus.SCHEDULED, "LIS", "OPO", "CS-TPA");
        flight.setVersion(0L);
        entityManager.persist(flight);
        entityManager.flush();

        var found = repository.findByAircraftRegistrationAndDepartureDateTime("CS-TPA", departure);
        assertTrue(found.isPresent());
        assertEquals("LIS", found.get().getOriginIataCode());
        assertEquals("OPO", found.get().getDestinationIataCode());
    }

    @Test
    void ensureAlignVersionWorks() {
        OffsetDateTime departure = OffsetDateTime.now().plusHours(3);
        OffsetDateTime arrival = departure.plusHours(2);

        ScheduledFlightJpaEntity flight = new ScheduledFlightJpaEntity(
                departure, arrival, FlightStatus.SCHEDULED, "LIS", "OPO", "CS-TPB");
        entityManager.persist(flight);
        entityManager.flush();

        repository.alignVersion(flight.getId(), 5L);
        entityManager.clear();

        var reloaded = repository.findById(flight.getId());
        assertTrue(reloaded.isPresent());
        assertEquals(5L, reloaded.get().getVersion());
    }

    @Test
    void ensureUniqueConstraintPreventsDuplicateAircraftAtSameDeparture() {
        OffsetDateTime departure = OffsetDateTime.now().plusHours(4);
        OffsetDateTime arrival = departure.plusHours(2);

        ScheduledFlightJpaEntity flight1 = new ScheduledFlightJpaEntity(
                departure, arrival, FlightStatus.SCHEDULED, "LIS", "OPO", "CS-TPC");
        repository.saveAndFlush(flight1);

        ScheduledFlightJpaEntity flight2 = new ScheduledFlightJpaEntity(
                departure, arrival.plusHours(1), FlightStatus.SCHEDULED, "LIS", "MAD", "CS-TPC");
        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(flight2));
    }
}
