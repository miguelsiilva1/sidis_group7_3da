package aisafe.routes.application;

import aisafe.routes.domain.Route;
import aisafe.routes.domain.RouteStatus;
import aisafe.shared.application.Versioned;
import aisafe.airports.domain.IataCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NetworkGraphServiceTest {

    @Mock
    private RoutesAcrossReplicas routes;

    @InjectMocks
    private NetworkGraphService service;

    @Test
    void findAlternativePathsReturnsCorrectPaths() {
        // A -> B -> C
        // A -> C (direct)
        Route r1 = new Route("OPO", "LIS", 45, 300.0, 100);
        
        Route r2 = new Route("LIS", "MAD", 45, 300.0, 100);
        
        Route r3 = new Route("OPO", "MAD", 45, 300.0, 100);

        when(routes.findAll()).thenReturn(versioned(r1, r2, r3));

        List<List<String>> paths = service.findAlternativePaths("OPO", "MAD");

        // Should return [OPO, LIS, MAD] because [OPO, MAD] is direct (size 2) and filter removes size <= 2
        assertEquals(1, paths.size());
        assertEquals(List.of("OPO", "LIS", "MAD"), paths.get(0));
    }

    @Test
    void findAlternativePathsDetectsCycles() {
        // OPO -> LIS -> OPO (cycle), LIS -> MAD
        Route r1 = new Route("OPO", "LIS", 45, 300.0, 100);

        Route r2 = new Route("LIS", "OPO", 45, 300.0, 100);

        Route r3 = new Route("LIS", "MAD", 45, 300.0, 100);

        when(routes.findAll()).thenReturn(versioned(r1, r2, r3));

        List<List<String>> paths = service.findAlternativePaths("OPO", "MAD");

        assertEquals(1, paths.size());
        assertEquals(List.of("OPO", "LIS", "MAD"), paths.get(0));
    }

    @Test
    void findAlternativePathsHandlesMaxDepth() {
        // AAA -> BBB -> CCC -> DDD -> EEE -> FFF -> GGG (depth 7 > 5)
        Route r1 = new Route("AAA", "BBB", 45, 300.0, 100);

        Route r2 = new Route("BBB", "CCC", 45, 300.0, 100);

        Route r3 = new Route("CCC", "DDD", 45, 300.0, 100);

        Route r4 = new Route("DDD", "EEE", 45, 300.0, 100);

        Route r5 = new Route("EEE", "FFF", 45, 300.0, 100);

        Route r6 = new Route("FFF", "GGG", 45, 300.0, 100);

        when(routes.findAll()).thenReturn(versioned(r1, r2, r3, r4, r5, r6));

        List<List<String>> paths = service.findAlternativePaths("AAA", "GGG");

        // The path length is 7 nodes. Max depth limit is 5.
        // It should return no paths because AAA -> BBB -> CCC -> DDD -> EEE -> FFF is size 6 (> 5), returning early.
        assertTrue(paths.isEmpty());
    }

    @Test
    void ensureInactiveRoutesAreNotUsed() {
        Route r1 = new Route("OPO", "LIS", 45, 300.0, 100);
        Route r2 = new Route("LIS", "MAD", 45, 300.0, 100);
        r2.changeStatus(RouteStatus.INACTIVE);
        when(routes.findAll()).thenReturn(versioned(r1, r2));

        assertTrue(service.findAlternativePaths("OPO", "MAD").isEmpty());
    }

    private static List<Versioned<Route>> versioned(Route... all) {
        return java.util.Arrays.stream(all).map(r -> new Versioned<>(r, 0L)).toList();
    }
}
