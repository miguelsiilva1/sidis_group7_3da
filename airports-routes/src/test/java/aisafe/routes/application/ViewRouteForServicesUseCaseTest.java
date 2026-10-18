package aisafe.routes.application;

import aisafe.airports.domain.IataCode;
import aisafe.routes.application.dtos.RouteForServicesResponse;
import aisafe.routes.domain.Route;
import aisafe.routes.domain.RouteNotFoundException;
import aisafe.routes.domain.RouteRepository;
import aisafe.routes.domain.RouteStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ViewRouteForServicesUseCaseTest {

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private RouteDistanceService routeDistanceService;

    @InjectMocks
    private ViewRouteForServicesUseCase useCase;

    @Test
    void ensureRouteIsReturnedWithItsDistance() {
        Route route = new Route("LIS", "OPO", 45, 280.0, 100);
        when(routeRepository.findByOriginAndDestination(new IataCode("LIS"), new IataCode("OPO"))).thenReturn(Optional.of(route));
        when(routeDistanceService.calculateDistanceKm(route)).thenReturn(274.25);

        RouteForServicesResponse response = useCase.execute("lis", "opo");

        assertEquals(RouteStatus.ACTIVE, response.status());
        assertEquals(100, response.minimumCapacity());
        assertEquals(280.0, response.minimumRange());
        assertEquals(274.25, response.distanceKm());
    }

    @Test
    void ensureRouteNotHeldHereIsNotFound() {
        when(routeRepository.findByOriginAndDestination(any(), any())).thenReturn(Optional.empty());

        assertThrows(RouteNotFoundException.class, () -> useCase.execute("LIS", "XXX"));
    }
}
