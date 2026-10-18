package aisafe.flightcopies.domain;

import aisafe.aircrafts.domain.RegistrationNumber;
import aisafe.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class FlightCopyTest {

    private static final OffsetDateTime DEPARTURE = OffsetDateTime.of(2026, 10, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final RegistrationNumber AIRCRAFT = new RegistrationNumber("CS-TKA");

    private static FlightCopy copy(long version, FlightCopyStatus status) {
        return new FlightCopy("f-1", DEPARTURE, DEPARTURE.plusHours(2), status, "LIS", "OPO", AIRCRAFT, version);
    }

    @Test
    void ensureValidCopyIsCreated() {
        FlightCopy copy = copy(3, FlightCopyStatus.COMPLETED);

        assertEquals("f-1", copy.getId());
        assertEquals(Duration.ofHours(2), copy.getDuration());
        assertEquals(3, copy.getVersion());
        assertFalse(copy.isCanceled());
    }

    @Test
    void ensureOnlyHigherVersionSupersedesStoredCopy() {
        FlightCopy stored = copy(2, FlightCopyStatus.SCHEDULED);

        assertTrue(copy(3, FlightCopyStatus.COMPLETED).supersedes(stored));
        assertFalse(copy(2, FlightCopyStatus.COMPLETED).supersedes(stored), "a retry of the same version changes nothing");
        assertFalse(copy(1, FlightCopyStatus.COMPLETED).supersedes(stored), "a late, older copy changes nothing");
    }

    @Test
    void ensureCanceledCopyIsRecognised() {
        assertTrue(copy(1, FlightCopyStatus.CANCELED).isCanceled());
    }

    @Test
    void ensureInvalidCopiesAreRejected() {
        assertThrows(DomainException.class, () -> new FlightCopy(" ", DEPARTURE, DEPARTURE.plusHours(1), FlightCopyStatus.SCHEDULED, "LIS", "OPO", AIRCRAFT, 0));
        assertThrows(DomainException.class, () -> new FlightCopy("f", null, DEPARTURE, FlightCopyStatus.SCHEDULED, "LIS", "OPO", AIRCRAFT, 0));
        assertThrows(DomainException.class, () -> new FlightCopy("f", DEPARTURE, DEPARTURE, FlightCopyStatus.SCHEDULED, "LIS", "OPO", AIRCRAFT, 0));
        assertThrows(DomainException.class, () -> new FlightCopy("f", DEPARTURE, DEPARTURE.plusHours(1), null, "LIS", "OPO", AIRCRAFT, 0));
        assertThrows(DomainException.class, () -> new FlightCopy("f", DEPARTURE, DEPARTURE.plusHours(1), FlightCopyStatus.SCHEDULED, "", "OPO", AIRCRAFT, 0));
        assertThrows(DomainException.class, () -> new FlightCopy("f", DEPARTURE, DEPARTURE.plusHours(1), FlightCopyStatus.SCHEDULED, "LIS", "OPO", null, 0));
        assertThrows(DomainException.class, () -> new FlightCopy("f", DEPARTURE, DEPARTURE.plusHours(1), FlightCopyStatus.SCHEDULED, "LIS", "OPO", AIRCRAFT, -1));
    }
}
