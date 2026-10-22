package aisafe.flights.infrastructure.persistence;

import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.IataCode;
import aisafe.flights.domain.RegistrationNumber;
import aisafe.flights.domain.ScheduledFlight;
import aisafe.flights.domain.ScheduledFlightRepository;
import aisafe.flights.infrastructure.persistence.jpa.ScheduledFlightJpaEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("jpa")
@Transactional
class ScheduledFlightJpaRepositoryAdditionalTest {

    @Autowired
    private ScheduledFlightRepository domainRepository;

    @Test
    void ensureSaveAndFindWorksWithoutForeignKeys() {
        ScheduledFlight flight = new ScheduledFlight(
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now().plusHours(2),
                FlightStatus.SCHEDULED,
                new IataCode("OPO"),
                new IataCode("LIS"),
                new RegistrationNumber("CS-XXX")
        );

        ScheduledFlight saved = domainRepository.save(flight);
        assertNotNull(saved.getId());
        assertEquals("CS-XXX", saved.getAircraftRegistrationNumber().getNumber());
        assertEquals("OPO", saved.getOriginCode().getCode());
        assertEquals("LIS", saved.getDestinationCode().getCode());
    }

    @Test
    void ensureScheduledFlightJpaEntityCoverage() {
        OffsetDateTime now = OffsetDateTime.now();
        ScheduledFlightJpaEntity entity = new ScheduledFlightJpaEntity();
        entity.setId(99L);
        entity.setDepartureDateTime(now);
        entity.setArrivalDateTime(now.plusHours(1));
        entity.setStatus(FlightStatus.SCHEDULED);
        entity.setOriginIataCode("LIS");
        entity.setDestinationIataCode("OPO");
        entity.setAircraftRegistration("CS-TPA");
        entity.setVersion(1L);

        assertEquals(99L, entity.getId());
        assertEquals(now, entity.getDepartureDateTime());
        assertEquals(now.plusHours(1), entity.getArrivalDateTime());
        assertEquals(FlightStatus.SCHEDULED, entity.getStatus());
        assertEquals("LIS", entity.getOriginIataCode());
        assertEquals("OPO", entity.getDestinationIataCode());
        assertEquals("CS-TPA", entity.getAircraftRegistration());
        assertEquals(1L, entity.getVersion());
    }

    @Test
    void ensureDeleteWithNullIdDoesNotThrowAndDoesNothing() {
        ScheduledFlight flight = new ScheduledFlight(
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now().plusHours(2),
                FlightStatus.SCHEDULED,
                new IataCode("OPO"),
                new IataCode("LIS"),
                new RegistrationNumber("CS-TPA")
        );
        assertDoesNotThrow(() -> domainRepository.delete(flight));
    }
}
