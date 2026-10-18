package aisafe.routes.application;

import aisafe.airports.domain.InvalidIataCodeException;
import aisafe.routes.application.dtos.RouteResponse;
import aisafe.routes.application.dtos.SearchRoutesRequest;
import aisafe.routes.domain.Route;
import aisafe.shared.application.Versioned;
import aisafe.shared.domain.PaginatedResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchRoutesUseCaseTest {

    @Mock
    private RoutesAcrossReplicas routes;

    @InjectMocks
    private SearchRoutesUseCase searchRoutes;

    @BeforeEach
    void setUp() {
        // As gathered from every replica: one entry per route, ordered by origin and destination
        lenient().when(routes.findAll()).thenReturn(List.of(
                new Versioned<>(new Route("LIS", "MAD", 60, 640.0, 120), 1L),
                new Versioned<>(new Route("LIS", "OPO", 45, 280.0, 100), 2L),
                new Versioned<>(new Route("OPO", "LIS", 45, 300.0, 150), 5L)));
    }

    @Test
    void ensureSearchByOriginAndDestination() {
        PaginatedResult<RouteResponse> result = searchRoutes.execute(new SearchRoutesRequest("OPO", "LIS", 0, 20));

        assertEquals(1L, result.totalElements());
        assertEquals(5L, result.data().get(0).version());
    }

    @Test
    void ensureSearchByOriginOnly() {
        PaginatedResult<RouteResponse> result = searchRoutes.execute(new SearchRoutesRequest("lis", null, 0, 20));

        assertEquals(2L, result.totalElements());
    }

    @Test
    void ensureSearchByDestinationOnly() {
        PaginatedResult<RouteResponse> result = searchRoutes.execute(new SearchRoutesRequest(null, "LIS", 0, 20));

        assertEquals(1L, result.totalElements());
        assertEquals("OPO", result.data().get(0).originIataCode());
    }

    @Test
    void ensureSearchWithNoParamsReturnsAll() {
        PaginatedResult<RouteResponse> result = searchRoutes.execute(new SearchRoutesRequest(null, null, 0, 20));

        assertEquals(3L, result.totalElements());
    }

    @Test
    void ensurePagesAreCutAfterGatheringEveryReplica() {
        PaginatedResult<RouteResponse> result = searchRoutes.execute(new SearchRoutesRequest(null, null, 1, 2));

        assertEquals(3L, result.totalElements());
        assertEquals(1, result.data().size());
        assertEquals("OPO", result.data().get(0).originIataCode());
    }

    @Test
    void ensureInvalidCodeIsRejected() {
        assertThrows(InvalidIataCodeException.class, () -> searchRoutes.execute(new SearchRoutesRequest("TOOLONG", null, 0, 20)));
        verify(routes, never()).findAll();
    }
}
