package aisafe.flights.application;

import aisafe.fleet.application.Fleet;
import aisafe.fleet.domain.AircraftConflictException;
import aisafe.fleet.domain.AircraftInfo;
import aisafe.fleet.domain.AircraftNotFoundException;
import aisafe.fleet.domain.FleetUnavailableException;
import aisafe.fleet.domain.FlightCopy;
import aisafe.fleet.domain.RouteConflictException;
import aisafe.fleet.domain.RouteInfo;
import aisafe.fleet.domain.RouteNotFoundException;
import aisafe.flights.application.dtos.FlightResponse;
import aisafe.flights.application.dtos.ScheduleFlightRequest;
import aisafe.flights.domain.AircraftIncompatibilityException;
import aisafe.flights.domain.AircraftUnavailableException;
import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.ScheduledFlight;
import aisafe.flights.domain.ScheduledFlightRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduleFlightUseCaseTest {

    @Mock
    private ScheduledFlightRepository scheduledFlightRepository;

    @Mock
    private FlightsAcrossReplicas flightsAcrossReplicas;

    @Mock
    private Fleet fleet;

    @Mock
    private SagaCompensationPort sagaCompensationPort;

    @InjectMocks
    private ScheduleFlightUseCase useCase;

    private ScheduleFlightRequest sampleRequest() {
        OffsetDateTime departure = OffsetDateTime.now().plusDays(1);
        OffsetDateTime arrival = departure.plusHours(2);
        return new ScheduleFlightRequest("CS-TPA", "OPO", "LIS", departure, arrival);
    }

    private RouteInfo activeRoute(int minCapacity, double minRange, double distanceKm) {
        return new RouteInfo("OPO", "LIS", "ACTIVE", 45, minCapacity, minRange, distanceKm);
    }

    private AircraftInfo availableAircraft(int seatCapacity, double range) {
        return new AircraftInfo("CS-TPA", "A320", "AVAILABLE", seatCapacity, range, 3.5);
    }

    @Test
    void ensureFlightIsScheduledSuccessfully() {
        ScheduleFlightRequest request = sampleRequest();
        when(fleet.findRoute("OPO", "LIS")).thenReturn(Optional.of(activeRoute(150, 300.0, 300.0)));
        when(fleet.findAircraft("CS-TPA")).thenReturn(Optional.of(availableAircraft(200, 2000.0)));
        when(scheduledFlightRepository.save(any(ScheduledFlight.class))).thenAnswer(inv -> {
            ScheduledFlight f = inv.getArgument(0);
            return new ScheduledFlight(1L, f.getDepartureDateTime(), f.getArrivalDateTime(),
                    f.getStatus(), f.getOriginCode(), f.getDestinationCode(), f.getAircraftRegistrationNumber(), 1L);
        });

        FlightResponse result = useCase.execute(request);

        assertNotNull(result);
        assertEquals("CS-TPA", result.aircraftId());
        assertEquals(FlightStatus.SCHEDULED, result.status());

        verify(fleet).sendFlightToAircraft(argThat(c -> "SCHEDULED".equals(c.status()) && "CS-TPA".equals(c.aircraftRegistration())));
        verify(fleet).sendFlightToRoutes(argThat(c -> "SCHEDULED".equals(c.status()) && "OPO".equals(c.originIataCode())));
    }

    @Test
    void ensureExceptionThrownWhenRouteNotFound() {
        ScheduleFlightRequest request = sampleRequest();
        when(fleet.findRoute("OPO", "LIS")).thenReturn(Optional.empty());

        assertThrows(RouteNotFoundException.class, () -> useCase.execute(request));
        verify(scheduledFlightRepository, never()).save(any());
    }

    @Test
    void ensureExceptionThrownWhenAircraftNotFound() {
        ScheduleFlightRequest request = sampleRequest();
        when(fleet.findRoute("OPO", "LIS")).thenReturn(Optional.of(activeRoute(150, 300.0, 300.0)));
        when(fleet.findAircraft("CS-TPA")).thenReturn(Optional.empty());

        assertThrows(AircraftNotFoundException.class, () -> useCase.execute(request));
        verify(scheduledFlightRepository, never()).save(any());
    }

    @Test
    void ensureExceptionThrownWhenAircraftCapacityInsufficient() {
        ScheduleFlightRequest request = sampleRequest();
        when(fleet.findRoute("OPO", "LIS")).thenReturn(Optional.of(activeRoute(300, 300.0, 300.0)));
        when(fleet.findAircraft("CS-TPA")).thenReturn(Optional.of(availableAircraft(200, 2000.0)));

        assertThrows(AircraftIncompatibilityException.class, () -> useCase.execute(request));
        verify(scheduledFlightRepository, never()).save(any());
    }

    @Test
    void ensureExceptionThrownWhenAircraftRangeInsufficient() {
        ScheduleFlightRequest request = sampleRequest();
        when(fleet.findRoute("OPO", "LIS")).thenReturn(Optional.of(activeRoute(150, 3000.0, 300.0)));
        when(fleet.findAircraft("CS-TPA")).thenReturn(Optional.of(availableAircraft(200, 2000.0)));

        assertThrows(AircraftIncompatibilityException.class, () -> useCase.execute(request));
        verify(scheduledFlightRepository, never()).save(any());
    }

    @Test
    void ensureExceptionThrownWhenRouteDistanceExceedsRange() {
        ScheduleFlightRequest request = sampleRequest();
        when(fleet.findRoute("OPO", "LIS")).thenReturn(Optional.of(activeRoute(150, 300.0, 2500.0)));
        when(fleet.findAircraft("CS-TPA")).thenReturn(Optional.of(availableAircraft(200, 2000.0)));

        assertThrows(AircraftIncompatibilityException.class, () -> useCase.execute(request));
        verify(scheduledFlightRepository, never()).save(any());
    }

    @Test
    void ensureExceptionThrownWhenAircraftScheduleOverlaps() {
        ScheduleFlightRequest request = sampleRequest();
        when(fleet.findRoute("OPO", "LIS")).thenReturn(Optional.of(activeRoute(150, 300.0, 300.0)));
        when(fleet.findAircraft("CS-TPA")).thenReturn(Optional.of(availableAircraft(200, 2000.0)));
        OffsetDateTime dep = request.departureDateTime().minusHours(1);
        OffsetDateTime arr = request.arrivalDateTime().plusHours(1);
        ScheduledFlight existing = new ScheduledFlight(
                99L, dep, arr, FlightStatus.SCHEDULED,
                new aisafe.flights.domain.IataCode("OPO"), new aisafe.flights.domain.IataCode("LIS"),
                new aisafe.flights.domain.RegistrationNumber("CS-TPA"), 1L);
        when(flightsAcrossReplicas.findAll()).thenReturn(List.of(new aisafe.shared.application.Versioned<>(existing, 1L)));

        assertThrows(AircraftUnavailableException.class, () -> useCase.execute(request));
        verify(scheduledFlightRepository, never()).save(any());
    }

    @Test
    void ensureExceptionThrownWhenAircraftUnderMaintenance() {
        ScheduleFlightRequest request = sampleRequest();
        when(fleet.findRoute("OPO", "LIS")).thenReturn(Optional.of(activeRoute(150, 300.0, 300.0)));
        AircraftInfo maintenanceAircraft = new AircraftInfo("CS-TPA", "A320", "UNDER_MAINTENANCE", 200, 2000.0, 3.5);
        when(fleet.findAircraft("CS-TPA")).thenReturn(Optional.of(maintenanceAircraft));

        assertThrows(AircraftUnavailableException.class, () -> useCase.execute(request));
        verify(scheduledFlightRepository, never()).save(any());
    }

    @Test
    void ensureExceptionThrownWhenAircraftInactive() {
        ScheduleFlightRequest request = sampleRequest();
        when(fleet.findRoute("OPO", "LIS")).thenReturn(Optional.of(activeRoute(150, 300.0, 300.0)));
        AircraftInfo inactiveAircraft = new AircraftInfo("CS-TPA", "A320", "INACTIVE", 200, 2000.0, 3.5);
        when(fleet.findAircraft("CS-TPA")).thenReturn(Optional.of(inactiveAircraft));

        assertThrows(AircraftUnavailableException.class, () -> useCase.execute(request));
        verify(scheduledFlightRepository, never()).save(any());
    }

    @Test
    void ensureC4ConflictCancelsFlight() {
        ScheduleFlightRequest request = sampleRequest();
        when(fleet.findRoute("OPO", "LIS")).thenReturn(Optional.of(activeRoute(150, 300.0, 300.0)));
        when(fleet.findAircraft("CS-TPA")).thenReturn(Optional.of(availableAircraft(200, 2000.0)));
        when(scheduledFlightRepository.save(any(ScheduledFlight.class))).thenAnswer(inv -> inv.getArgument(0));

        doThrow(new AircraftConflictException("409 Conflict")).when(fleet).sendFlightToAircraft(any());

        assertThrows(AircraftUnavailableException.class, () -> useCase.execute(request));

        ArgumentCaptor<ScheduledFlight> captor = ArgumentCaptor.forClass(ScheduledFlight.class);
        verify(scheduledFlightRepository, atLeast(2)).save(captor.capture());
        ScheduledFlight lastSaved = captor.getValue();
        assertEquals(FlightStatus.CANCELED, lastSaved.getStatus());
        verify(fleet, never()).sendFlightToRoutes(any());
    }

    @Test
    void ensureC5ConflictCompensatesC4WithCanceledAndCancelsFlight() {
        ScheduleFlightRequest request = sampleRequest();
        when(fleet.findRoute("OPO", "LIS")).thenReturn(Optional.of(activeRoute(150, 300.0, 300.0)));
        when(fleet.findAircraft("CS-TPA")).thenReturn(Optional.of(availableAircraft(200, 2000.0)));
        when(scheduledFlightRepository.save(any(ScheduledFlight.class))).thenAnswer(inv -> inv.getArgument(0));

        doNothing().when(fleet).sendFlightToAircraft(argThat(c -> "SCHEDULED".equals(c.status())));
        doThrow(new RouteConflictException("409 Conflict")).when(fleet).sendFlightToRoutes(any());

        assertThrows(RouteConflictException.class, () -> useCase.execute(request));

        // C4 compensation called with CANCELED
        verify(fleet).sendFlightToAircraft(argThat(c -> "CANCELED".equals(c.status()) && "CS-TPA".equals(c.aircraftRegistration())));

        // Local flight canceled
        ArgumentCaptor<ScheduledFlight> captor = ArgumentCaptor.forClass(ScheduledFlight.class);
        verify(scheduledFlightRepository, atLeast(2)).save(captor.capture());
        ScheduledFlight lastSaved = captor.getValue();
        assertEquals(FlightStatus.CANCELED, lastSaved.getStatus());
    }

    @Test
    void ensureCompensationNetworkFailureIsRecorded() {
        ScheduleFlightRequest request = sampleRequest();
        when(fleet.findRoute("OPO", "LIS")).thenReturn(Optional.of(activeRoute(150, 300.0, 300.0)));
        when(fleet.findAircraft("CS-TPA")).thenReturn(Optional.of(availableAircraft(200, 2000.0)));
        when(scheduledFlightRepository.save(any(ScheduledFlight.class))).thenAnswer(inv -> inv.getArgument(0));

        doNothing().when(fleet).sendFlightToAircraft(argThat(c -> "SCHEDULED".equals(c.status())));
        doThrow(new RouteConflictException("409 Conflict")).when(fleet).sendFlightToRoutes(any());
        doThrow(new FleetUnavailableException("Network timeout")).when(fleet).sendFlightToAircraft(argThat(c -> "CANCELED".equals(c.status())));

        assertThrows(RouteConflictException.class, () -> useCase.execute(request));

        // Compensation saved for retry
        verify(sagaCompensationPort).recordFailedCompensation(
                argThat(c -> "CANCELED".equals(c.status()) && "CS-TPA".equals(c.aircraftRegistration())),
                eq("AIRCRAFT"),
                contains("Network timeout")
        );

        // Local flight still marked CANCELED
        ArgumentCaptor<ScheduledFlight> captor = ArgumentCaptor.forClass(ScheduledFlight.class);
        verify(scheduledFlightRepository, atLeast(2)).save(captor.capture());
        assertEquals(FlightStatus.CANCELED, captor.getValue().getStatus());
    }
}
