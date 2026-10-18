package aisafe.routes.application;

import aisafe.routes.application.dtos.TotalDistanceResponse;
import aisafe.routes.domain.Route;
import aisafe.routes.domain.RouteStatus;
import aisafe.shared.application.Versioned;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalculateTotalNetworkDistanceUseCaseTest {

    @Mock
    private RoutesAcrossReplicas routes;
    @Mock
    private RouteDistanceService routeDistanceService;

    @InjectMocks
    private CalculateTotalNetworkDistanceUseCase useCase;

    @Test
    void executeReturnsTotalDistance() {
        Route r1 = new Route("OPO", "LIS", 45, 300.0, 100);
        Route r2 = new Route("LIS", "MAD", 60, 640.0, 120);
        Route inactive = new Route("MAD", "CDG", 120, 1050.0, 120);
        inactive.changeStatus(RouteStatus.INACTIVE);
        when(routes.findAll()).thenReturn(List.of(new Versioned<>(r1, 1L), new Versioned<>(r2, 1L), new Versioned<>(inactive, 1L)));
        when(routeDistanceService.calculateDistanceKm(r1)).thenReturn(100.0);
        when(routeDistanceService.calculateDistanceKm(r2)).thenReturn(200.0);

        TotalDistanceResponse result = useCase.execute();

        assertEquals(300.0, result.totalDistance());
        assertEquals("km", result.unit());
    }
}
