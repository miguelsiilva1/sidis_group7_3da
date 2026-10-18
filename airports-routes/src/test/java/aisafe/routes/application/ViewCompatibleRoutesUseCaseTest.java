package aisafe.routes.application;

import aisafe.fleet.application.Fleet;
import aisafe.fleet.domain.AircraftInfo;
import aisafe.fleet.domain.AircraftNotFoundException;
import aisafe.routes.application.dtos.CompatibleRouteResponse;
import aisafe.routes.domain.Route;
import aisafe.routes.domain.RouteStatus;
import aisafe.shared.application.Versioned;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ViewCompatibleRoutesUseCaseTest {

    @Mock
    private Fleet fleet;

    @Mock
    private RoutesAcrossReplicas routes;

    @InjectMocks
    private ViewCompatibleRoutesUseCase useCase;

    private static Versioned<Route> route(String origin, String destination, double range, int capacity, RouteStatus status) {
        Route route = new Route(origin, destination, 60, range, capacity);
        route.changeStatus(status);
        return new Versioned<>(route, 0L);
    }

    @Test
    void ensureOnlyActiveRoutesWithinRangeAndCapacityAreReturned() {
        when(fleet.findAircraft("CS-TKA")).thenReturn(Optional.of(
                new AircraftInfo("CS-TKA", "Airbus A320neo", "AVAILABLE", 150, 1000.0, 3.0)));
        when(routes.findAll()).thenReturn(List.of(
                route("LIS", "MAD", 640.0, 120, RouteStatus.ACTIVE),
                route("LIS", "OPO", 280.0, 100, RouteStatus.ACTIVE),
                route("LIS", "CDG", 1730.0, 150, RouteStatus.ACTIVE),
                route("OPO", "MAD", 850.0, 200, RouteStatus.ACTIVE),
                route("MAD", "LIS", 640.0, 120, RouteStatus.INACTIVE)));

        List<CompatibleRouteResponse> result = useCase.execute("CS-TKA");

        assertEquals(List.of("LIS-MAD", "LIS-OPO"),
                result.stream().map(r -> r.originIataCode() + "-" + r.destinationIataCode()).toList());
    }

    @Test
    void ensureUnknownAircraftIsNotFoundAndRoutesAreNotGathered() {
        when(fleet.findAircraft("CS-XXX")).thenReturn(Optional.empty());

        assertThrows(AircraftNotFoundException.class, () -> useCase.execute("CS-XXX"));
        verifyNoInteractions(routes);
    }
}
