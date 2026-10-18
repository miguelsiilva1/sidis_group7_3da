package aisafe.routes.application;

import aisafe.flightcopies.application.FlightCopiesAcrossReplicas;
import aisafe.flightcopies.domain.FlightCopy;
import aisafe.flightcopies.domain.FlightCopyStatus;
import aisafe.routes.application.dtos.ActiveRouteResponse;
import aisafe.routes.domain.InvalidSortParameterException;
import aisafe.routes.domain.Route;
import aisafe.routes.domain.RouteStatus;
import aisafe.shared.application.Versioned;
import aisafe.shared.domain.InvalidListingCriteriaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListActiveRoutesUseCaseTest {

    @Mock
    private RoutesAcrossReplicas routes;
    @Mock
    private FlightCopiesAcrossReplicas flightCopies;
    @Mock
    private RouteDistanceService routeDistanceService;

    @InjectMocks
    private ListActiveRoutesUseCase useCase;

    private static Versioned<Route> route(String origin, String destination) {
        return new Versioned<>(new Route(origin, destination, 45, 300.0, 100), 0L);
    }

    private static List<FlightCopy> flights(String origin, String destination, int count) {
        OffsetDateTime departure = OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        List<FlightCopy> copies = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            copies.add(new FlightCopy(UUID.randomUUID().toString(), departure, departure.plusHours(1),
                    FlightCopyStatus.SCHEDULED, origin, destination, "CS-TKA", 0));
        }
        return copies;
    }

    @Test
    void executeReturnsActiveRoutes() {
        Versioned<Route> route = route("OPO", "LIS");
        when(routes.findAll()).thenReturn(List.of(route));
        when(routeDistanceService.calculateDistanceKm(route.value())).thenReturn(300.0);
        when(flightCopies.findAll()).thenReturn(flights("OPO", "LIS", 5));

        List<ActiveRouteResponse> result = useCase.execute("active", "distance");

        assertEquals(1, result.size());
        assertEquals("OPO", result.get(0).originIataCode());
        assertEquals(300.0, result.get(0).distanceKm());
        assertEquals(5L, result.get(0).popularity());
    }

    @Test
    void ensureInactiveRoutesAreLeftOut() {
        Versioned<Route> active = route("OPO", "LIS");
        Versioned<Route> inactive = route("LIS", "MAD");
        inactive.value().changeStatus(RouteStatus.INACTIVE);
        when(routes.findAll()).thenReturn(List.of(active, inactive));
        when(routeDistanceService.calculateDistanceKm(active.value())).thenReturn(300.0);
        when(flightCopies.findAll()).thenReturn(List.of());

        List<ActiveRouteResponse> result = useCase.execute("active", "distance");

        assertEquals(1, result.size());
        assertEquals("OPO", result.get(0).originIataCode());
        assertEquals(0L, result.get(0).popularity());
    }

    @Test
    void ensureExecuteThrowsInvalidSortParameterExceptionWhenSortByIsInvalid() {
        assertThrows(InvalidSortParameterException.class, () -> {
            useCase.execute("active", "invalidSort");
        });
    }

    @Test
    void ensureExecuteThrowsInvalidListingCriteriaExceptionWhenStatusIsInvalid() {
        assertThrows(InvalidListingCriteriaException.class, () -> {
            useCase.execute("inactive", "distance");
        });
    }

    @Test
    void ensureExecuteSortsByPopularityCorrectly() {
        Versioned<Route> route1 = route("OPO", "LIS");
        Versioned<Route> route2 = route("LIS", "MAD");
        when(routes.findAll()).thenReturn(List.of(route1, route2));
        when(routeDistanceService.calculateDistanceKm(route1.value())).thenReturn(300.0);
        when(routeDistanceService.calculateDistanceKm(route2.value())).thenReturn(500.0);
        List<FlightCopy> copies = new ArrayList<>(flights("OPO", "LIS", 5));
        copies.addAll(flights("LIS", "MAD", 10));
        when(flightCopies.findAll()).thenReturn(copies);

        // Popularity: route2 has 10, route1 has 5. Reversed order: route2, then route1
        List<ActiveRouteResponse> result = useCase.execute("active", "popularity");

        assertEquals(2, result.size());
        assertEquals("LIS", result.get(0).originIataCode()); // route2
        assertEquals(10L, result.get(0).popularity());
        assertEquals("OPO", result.get(1).originIataCode()); // route1
    }

    @Test
    void ensureExecuteDefaultsToDistanceWhenSortByIsNull() {
        Versioned<Route> route1 = route("OPO", "LIS");
        Versioned<Route> route2 = route("LIS", "MAD");
        when(routes.findAll()).thenReturn(List.of(route1, route2));
        when(routeDistanceService.calculateDistanceKm(route1.value())).thenReturn(300.0);
        when(routeDistanceService.calculateDistanceKm(route2.value())).thenReturn(100.0);
        when(flightCopies.findAll()).thenReturn(List.of());

        // Distance: route2 has 100.0, route1 has 300.0. Sorted: route2, then route1
        List<ActiveRouteResponse> result = useCase.execute(null, null);

        assertEquals(2, result.size());
        assertEquals("LIS", result.get(0).originIataCode()); // route2
        assertEquals("OPO", result.get(1).originIataCode()); // route1
    }
}
