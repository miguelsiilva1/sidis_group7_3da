package aisafe.flightcopies.application;

import aisafe.airports.domain.IataCode;
import aisafe.flightcopies.application.dtos.FlightCopyResponse;
import aisafe.flightcopies.application.dtos.RecordFlightCopyRequest;
import aisafe.flightcopies.domain.FlightCopy;
import aisafe.flightcopies.domain.FlightCopyRepository;
import aisafe.flightcopies.domain.FlightCopyStatus;
import aisafe.flightcopies.domain.RouteUnavailableForFlightException;
import aisafe.routes.application.dtos.RouteForServicesResponse;
import aisafe.routes.domain.Route;
import aisafe.routes.domain.RouteNotFoundException;
import aisafe.routes.domain.RouteRepository;
import aisafe.routes.domain.RouteStatus;
import aisafe.shared.application.PeerQueries;
import aisafe.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RecordFlightCopyUseCaseTest {

    private static final OffsetDateTime DEPARTURE = OffsetDateTime.of(2026, 11, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final IataCode LIS = new IataCode("LIS");
    private static final IataCode OPO = new IataCode("OPO");

    private FlightCopyRepository flightCopyRepository;
    private RouteRepository routeRepository;
    private PeerQueries peerQueries;
    private RecordFlightCopyUseCase useCase;

    @BeforeEach
    void setUp() {
        flightCopyRepository = mock(FlightCopyRepository.class);
        routeRepository = mock(RouteRepository.class);
        peerQueries = mock(PeerQueries.class);
        useCase = new RecordFlightCopyUseCase(flightCopyRepository, routeRepository, peerQueries);
        when(flightCopyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static RecordFlightCopyRequest request(String status, long version) {
        return new RecordFlightCopyRequest("f-1", DEPARTURE, DEPARTURE.plusHours(2), status, "lis", "opo", "cs-tka", version);
    }

    private static Route route(RouteStatus status) {
        Route route = new Route("LIS", "OPO", 45, 280.0, 100);
        route.changeStatus(status);
        return route;
    }

    private static RouteForServicesResponse remoteRoute(RouteStatus status) {
        return new RouteForServicesResponse("LIS", "OPO", status, 45, 100, 280.0, 274.0);
    }

    @Test
    void ensureNewFlightOnActiveLocalRouteIsStored() {
        when(flightCopyRepository.findById("f-1")).thenReturn(Optional.empty());
        when(routeRepository.findByOriginAndDestination(LIS, OPO)).thenReturn(Optional.of(route(RouteStatus.ACTIVE)));

        FlightCopyResponse response = useCase.execute(request("SCHEDULED", 1));

        assertEquals("LIS", response.originIataCode());
        assertEquals("OPO", response.destinationIataCode());
        assertEquals("CS-TKA", response.aircraftRegistration());
        verify(flightCopyRepository).save(any());
        verifyNoInteractions(peerQueries);
    }

    @Test
    void ensureRouteHeldByAPeerIsAskedForAndAccepted() {
        when(flightCopyRepository.findById("f-1")).thenReturn(Optional.empty());
        when(routeRepository.findByOriginAndDestination(LIS, OPO)).thenReturn(Optional.empty());
        when(peerQueries.fetchFirst(eq("/internal/routes/LIS/OPO"), eq(RouteForServicesResponse.class)))
                .thenReturn(Optional.of(remoteRoute(RouteStatus.ACTIVE)));

        useCase.execute(request("SCHEDULED", 1));

        verify(flightCopyRepository).save(any());
    }

    @Test
    void ensureFutureFlightOnInactiveRouteIsRejected() {
        when(flightCopyRepository.findById("f-1")).thenReturn(Optional.empty());
        when(routeRepository.findByOriginAndDestination(LIS, OPO)).thenReturn(Optional.empty());
        when(peerQueries.fetchFirst(eq("/internal/routes/LIS/OPO"), eq(RouteForServicesResponse.class)))
                .thenReturn(Optional.of(remoteRoute(RouteStatus.INACTIVE)));

        assertThrows(RouteUnavailableForFlightException.class, () -> useCase.execute(request("SCHEDULED", 1)));
        verify(flightCopyRepository, never()).save(any());
    }

    @Test
    void ensureCompletedFlightOnInactiveRouteIsStored() {
        when(flightCopyRepository.findById("f-1")).thenReturn(Optional.empty());
        when(routeRepository.findByOriginAndDestination(LIS, OPO)).thenReturn(Optional.of(route(RouteStatus.INACTIVE)));

        useCase.execute(request("COMPLETED", 1));

        verify(flightCopyRepository).save(any());
    }

    @Test
    void ensureUnknownRouteIsRejected() {
        when(flightCopyRepository.findById("f-1")).thenReturn(Optional.empty());
        when(routeRepository.findByOriginAndDestination(LIS, OPO)).thenReturn(Optional.empty());
        when(peerQueries.fetchFirst(any(), eq(RouteForServicesResponse.class))).thenReturn(Optional.empty());

        assertThrows(RouteNotFoundException.class, () -> useCase.execute(request("SCHEDULED", 1)));
        verify(flightCopyRepository, never()).save(any());
    }

    @Test
    void ensureRepeatedOrOlderCopyChangesNothing() {
        FlightCopy stored = new FlightCopy("f-1", DEPARTURE, DEPARTURE.plusHours(2), FlightCopyStatus.DELAYED,
                "LIS", "OPO", "CS-TKA", 3);
        when(flightCopyRepository.findById("f-1")).thenReturn(Optional.of(stored));

        FlightCopyResponse response = useCase.execute(request("SCHEDULED", 2));

        assertEquals("DELAYED", response.status());
        assertEquals(3, response.version());
        verify(flightCopyRepository, never()).save(any());
        verifyNoInteractions(routeRepository, peerQueries);
    }

    @Test
    void ensureNewerVersionReplacesStoredCopyWithoutCheckingTheRouteAgain() {
        FlightCopy stored = new FlightCopy("f-1", DEPARTURE, DEPARTURE.plusHours(2), FlightCopyStatus.SCHEDULED,
                "LIS", "OPO", "CS-TKA", 1);
        when(flightCopyRepository.findById("f-1")).thenReturn(Optional.of(stored));

        FlightCopyResponse response = useCase.execute(request("COMPLETED", 2));

        assertEquals("COMPLETED", response.status());
        verify(flightCopyRepository).save(any());
        verifyNoInteractions(routeRepository);
    }

    @Test
    void ensureCanceledCopyOfUnknownFlightIsStoredWithoutCheckingTheRoute() {
        when(flightCopyRepository.findById("f-1")).thenReturn(Optional.empty());

        useCase.execute(request("CANCELED", 1));

        verify(flightCopyRepository).save(any());
        verifyNoInteractions(routeRepository, peerQueries);
    }

    @Test
    void ensureInvalidStatusIsRejected() {
        assertThrows(DomainException.class, () -> useCase.execute(request("LOST", 1)));
    }
}
