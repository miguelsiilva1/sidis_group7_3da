package aisafe.routes.application;

import aisafe.airports.domain.IataCode;
import aisafe.fleet.application.Fleet;
import aisafe.fleet.domain.AircraftInfo;
import aisafe.fleet.domain.AircraftNotFoundException;
import aisafe.routes.application.dtos.FuelEfficiencyResponse;
import aisafe.routes.application.dtos.RouteForServicesResponse;
import aisafe.routes.domain.Route;
import aisafe.routes.domain.RouteNotFoundException;
import aisafe.routes.domain.RouteRepository;
import aisafe.routes.domain.RouteStatus;
import aisafe.shared.application.PeerQueries;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalculateFuelEfficiencyUseCaseTest {

    @Mock
    private Fleet fleet;

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private PeerQueries peerQueries;

    @InjectMocks
    private CalculateFuelEfficiencyUseCase useCase;

    private static final AircraftInfo TKA = new AircraftInfo("CS-TKA", "Airbus A320neo", "AVAILABLE", 180, 6570.0, 2.5);

    @Test
    void ensureFuelIsConsumptionTimesMinimumRangeOfLocalRoute() {
        when(routeRepository.findByOriginAndDestination(new IataCode("LIS"), new IataCode("OPO")))
                .thenReturn(Optional.of(new Route("LIS", "OPO", 45, 280.0, 100)));
        when(fleet.findAircraft("CS-TKA")).thenReturn(Optional.of(TKA));

        FuelEfficiencyResponse response = useCase.execute("CS-TKA", "lis", "opo");

        assertEquals("CS-TKA", response.registrationNumber());
        assertEquals(2.5, response.fuelConsumptionPerDistanceUnit());
        assertEquals("LIS", response.routeOrigin());
        assertEquals(700.0, response.fuelNeededForRoute());
        verifyNoInteractions(peerQueries);
    }

    @Test
    void ensureRouteHeldByAPeerIsUsed() {
        when(routeRepository.findByOriginAndDestination(any(), any())).thenReturn(Optional.empty());
        when(peerQueries.fetchFirst("/internal/routes/LIS/MAD", RouteForServicesResponse.class))
                .thenReturn(Optional.of(new RouteForServicesResponse("LIS", "MAD", RouteStatus.ACTIVE, 60, 120, 640.0, 502.0)));
        when(fleet.findAircraft("CS-TKA")).thenReturn(Optional.of(TKA));

        assertEquals(1600.0, useCase.execute("CS-TKA", "LIS", "MAD").fuelNeededForRoute());
    }

    @Test
    void ensureUnknownRouteIsNotFoundBeforeAskingTheFleet() {
        when(routeRepository.findByOriginAndDestination(any(), any())).thenReturn(Optional.empty());
        when(peerQueries.fetchFirst(any(), eq(RouteForServicesResponse.class))).thenReturn(Optional.empty());

        assertThrows(RouteNotFoundException.class, () -> useCase.execute("CS-TKA", "LIS", "XXX"));
        verifyNoInteractions(fleet);
    }

    @Test
    void ensureUnknownAircraftIsNotFound() {
        when(routeRepository.findByOriginAndDestination(any(), any()))
                .thenReturn(Optional.of(new Route("LIS", "OPO", 45, 280.0, 100)));
        when(fleet.findAircraft("CS-XXX")).thenReturn(Optional.empty());

        assertThrows(AircraftNotFoundException.class, () -> useCase.execute("CS-XXX", "LIS", "OPO"));
    }
}
