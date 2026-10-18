package aisafe.flightcopies.application;

import aisafe.aircrafts.application.dtos.AircraftForServicesResponse;
import aisafe.aircrafts.domain.*;
import aisafe.flightcopies.application.dtos.FlightCopyResponse;
import aisafe.flightcopies.application.dtos.RecordFlightCopyRequest;
import aisafe.flightcopies.domain.AircraftUnavailableForFlightException;
import aisafe.flightcopies.domain.FlightCopy;
import aisafe.flightcopies.domain.FlightCopyRepository;
import aisafe.flightcopies.domain.FlightCopyStatus;
import aisafe.shared.application.PeerQueries;
import aisafe.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RecordFlightCopyUseCaseTest {

    private static final OffsetDateTime DEPARTURE = OffsetDateTime.of(2026, 11, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final RegistrationNumber REGISTRATION = new RegistrationNumber("CS-TKA");

    private FlightCopyRepository flightCopyRepository;
    private AircraftRepository aircraftRepository;
    private PeerQueries peerQueries;
    private RecordFlightCopyUseCase useCase;

    @BeforeEach
    void setUp() {
        flightCopyRepository = mock(FlightCopyRepository.class);
        aircraftRepository = mock(AircraftRepository.class);
        peerQueries = mock(PeerQueries.class);
        useCase = new RecordFlightCopyUseCase(flightCopyRepository, aircraftRepository, peerQueries);
        when(flightCopyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static RecordFlightCopyRequest request(String status, long version) {
        return new RecordFlightCopyRequest("f-1", DEPARTURE, DEPARTURE.plusHours(2), status, "lis", "opo", "cs-tka", version);
    }

    private static Aircraft aircraft(AircraftStatus status) {
        AircraftModel model = new AircraftModel("A320", Manufacturer.AIRBUS, 26730.0, 6150.0, 833.0, null, 180);
        return new Aircraft(status, LocalDate.of(2020, 1, 1), model, REGISTRATION, 150, 5000.0, List.of());
    }

    @Test
    void ensureNewFlightForAvailableLocalAircraftIsStored() {
        when(flightCopyRepository.findById("f-1")).thenReturn(Optional.empty());
        when(aircraftRepository.findByRegistrationNumber(REGISTRATION)).thenReturn(Optional.of(aircraft(AircraftStatus.AVAILABLE)));

        FlightCopyResponse response = useCase.execute(request("SCHEDULED", 1));

        assertEquals("SCHEDULED", response.status());
        assertEquals("LIS", response.originIataCode());
        assertEquals("CS-TKA", response.aircraftRegistration());
        verify(flightCopyRepository).save(any());
        verifyNoInteractions(peerQueries);
    }

    @Test
    void ensureAircraftHeldByAPeerIsLookedUpThere() {
        when(flightCopyRepository.findById("f-1")).thenReturn(Optional.empty());
        when(aircraftRepository.findByRegistrationNumber(REGISTRATION)).thenReturn(Optional.empty());
        when(peerQueries.fetchFirst(eq("/internal/aircrafts/CS-TKA"), eq(AircraftForServicesResponse.class)))
                .thenReturn(Optional.of(new AircraftForServicesResponse("CS-TKA", "A320", AircraftStatus.AVAILABLE, 150, 5000.0, 4.0)));

        useCase.execute(request("SCHEDULED", 1));

        verify(flightCopyRepository).save(any());
    }

    @Test
    void ensureNewFlightForUnknownAircraftIsRejected() {
        when(flightCopyRepository.findById("f-1")).thenReturn(Optional.empty());
        when(aircraftRepository.findByRegistrationNumber(REGISTRATION)).thenReturn(Optional.empty());
        when(peerQueries.fetchFirst(any(), eq(AircraftForServicesResponse.class))).thenReturn(Optional.empty());

        assertThrows(AircraftNotFoundException.class, () -> useCase.execute(request("SCHEDULED", 1)));
        verify(flightCopyRepository, never()).save(any());
    }

    @Test
    void ensureNewFutureFlightForAircraftUnderMaintenanceIsRejected() {
        when(flightCopyRepository.findById("f-1")).thenReturn(Optional.empty());
        when(aircraftRepository.findByRegistrationNumber(REGISTRATION)).thenReturn(Optional.of(aircraft(AircraftStatus.UNDER_MAINTENANCE)));

        assertThrows(AircraftUnavailableForFlightException.class, () -> useCase.execute(request("SCHEDULED", 1)));
        verify(flightCopyRepository, never()).save(any());
    }

    @Test
    void ensureCompletedFlightForAircraftNowUnderMaintenanceIsStored() {
        when(flightCopyRepository.findById("f-1")).thenReturn(Optional.empty());
        when(aircraftRepository.findByRegistrationNumber(REGISTRATION)).thenReturn(Optional.of(aircraft(AircraftStatus.UNDER_MAINTENANCE)));

        assertEquals("COMPLETED", useCase.execute(request("COMPLETED", 1)).status());
    }

    @Test
    void ensureCanceledCopyIsStoredWithoutCheckingTheAircraft() {
        when(flightCopyRepository.findById("f-1")).thenReturn(Optional.empty());

        assertEquals("CANCELED", useCase.execute(request("CANCELED", 1)).status());
        verifyNoInteractions(aircraftRepository, peerQueries);
    }

    @Test
    void ensureRepeatedOrOlderCopyChangesNothing() {
        FlightCopy stored = new FlightCopy("f-1", DEPARTURE, DEPARTURE.plusHours(2), FlightCopyStatus.COMPLETED,
                "LIS", "OPO", REGISTRATION, 5);
        when(flightCopyRepository.findById("f-1")).thenReturn(Optional.of(stored));

        FlightCopyResponse sameVersion = useCase.execute(request("SCHEDULED", 5));
        FlightCopyResponse olderVersion = useCase.execute(request("SCHEDULED", 4));

        assertEquals("COMPLETED", sameVersion.status());
        assertEquals(5, olderVersion.version());
        verify(flightCopyRepository, never()).save(any());
    }

    @Test
    void ensureNewerVersionOfKnownFlightReplacesItWithoutCheckingTheAircraft() {
        FlightCopy stored = new FlightCopy("f-1", DEPARTURE, DEPARTURE.plusHours(2), FlightCopyStatus.SCHEDULED,
                "LIS", "OPO", REGISTRATION, 1);
        when(flightCopyRepository.findById("f-1")).thenReturn(Optional.of(stored));

        assertEquals("COMPLETED", useCase.execute(request("COMPLETED", 2)).status());
        verifyNoInteractions(aircraftRepository, peerQueries);
    }

    @Test
    void ensureInvalidStatusIsRejected() {
        assertThrows(DomainException.class, () -> useCase.execute(request("LANDED", 1)));
    }
}
