package aisafe.flights.infrastructure.persistence;

import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.IataCode;
import aisafe.flights.domain.RegistrationNumber;
import aisafe.flights.domain.ScheduledFlight;
import aisafe.flights.domain.ScheduledFlightRepository;
import aisafe.flights.infrastructure.persistence.jpa.ScheduledFlightJpaEntity;
import aisafe.flights.infrastructure.persistence.jpa.ScheduledFlightMapper;
import aisafe.flights.infrastructure.persistence.jpa.SpringDataScheduledFlightRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("jpa")
@Transactional
class SpringDataScheduledFlightRepositoryTest {

    @Autowired
    private SpringDataScheduledFlightRepository flightRepo;

    @Autowired
    private ScheduledFlightRepository domainRepository;

    private OffsetDateTime now;

    @BeforeEach
    void setUp() {
        flightRepo.deleteAll();

        now = OffsetDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

        // 3 completed flights for OPO -> LIS with CS-TPA
        flightRepo.save(new ScheduledFlightJpaEntity(101L, now.minusDays(5), now.minusDays(5).plusHours(1), FlightStatus.COMPLETED, "OPO", "LIS", "CS-TPA"));
        flightRepo.save(new ScheduledFlightJpaEntity(102L, now.minusDays(4), now.minusDays(4).plusHours(1), FlightStatus.COMPLETED, "OPO", "LIS", "CS-TPA"));
        flightRepo.save(new ScheduledFlightJpaEntity(103L, now.minusDays(1), now.minusDays(1).plusHours(1), FlightStatus.COMPLETED, "OPO", "LIS", "CS-TPA"));

        // 1 completed flight for LIS -> MAD with CS-TPA
        flightRepo.save(new ScheduledFlightJpaEntity(104L, now.minusDays(2), now.minusDays(2).plusHours(1), FlightStatus.COMPLETED, "LIS", "MAD", "CS-TPA"));

        // 1 scheduled (not completed) flight for LIS -> MAD with CS-TPA
        flightRepo.save(new ScheduledFlightJpaEntity(105L, now.plusDays(1), now.plusDays(1).plusHours(1), FlightStatus.SCHEDULED, "LIS", "MAD", "CS-TPA"));
    }

    @Test
    void ensureDomainRepositoryCountAndFindAll() {
        assertEquals(5, domainRepository.count());
        assertEquals(5, domainRepository.findAll().size());
    }

    @Test
    void ensureDomainRepositoryFindById() {
        List<ScheduledFlight> all = domainRepository.findAll();
        assertFalse(all.isEmpty());
        Long id = all.get(0).getId();
        Optional<ScheduledFlight> found = domainRepository.findById(id);
        assertTrue(found.isPresent());
        assertEquals(id, found.get().getId());
    }

    @Test
    void ensureDomainRepositorySaveAndDelete() {
        ScheduledFlight flight = new ScheduledFlight(
                null,
                now.plusDays(10),
                now.plusDays(10).plusHours(2),
                FlightStatus.SCHEDULED,
                new IataCode("OPO"),
                new IataCode("LIS"),
                new RegistrationNumber("CS-TPA")
        );

        ScheduledFlight saved = domainRepository.save(flight);
        assertNotNull(saved.getId());
        assertEquals(6, domainRepository.count());

        domainRepository.delete(saved);
        assertEquals(5, domainRepository.count());
    }

    @Test
    void ensureDomainRepositoryFindByAircraftRegistrationAndDepartureDateTime() {
        var found = domainRepository.findByAircraftRegistrationAndDepartureDateTime(
                new RegistrationNumber("CS-TPA"), now.minusDays(5));
        assertTrue(found.isPresent());
        assertEquals("OPO", found.get().getOriginCode().getCode());
        assertEquals("LIS", found.get().getDestinationCode().getCode());

        var notFound = domainRepository.findByAircraftRegistrationAndDepartureDateTime(
                new RegistrationNumber("CS-TPA"), now.plusYears(1));
        assertTrue(notFound.isEmpty());
    }

    @Test
    void ensureScheduledFlightMapperNullAndConstructor() throws Exception {
        assertNull(ScheduledFlightMapper.toDomain(null));
        Constructor<ScheduledFlightMapper> constructor = ScheduledFlightMapper.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        assertThrows(InvocationTargetException.class, constructor::newInstance);
    }
}
