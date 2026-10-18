package aisafe.airports.application;

import aisafe.airports.domain.AirportNotFoundException;
import aisafe.airports.domain.AirportRepository;
import aisafe.airports.domain.IataCode;
import aisafe.routes.application.RoutesAcrossReplicas;
import aisafe.routes.application.dtos.RouteResponse;
import aisafe.routes.domain.Route;
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
class ViewAirportRoutesUseCaseTest {

    @Mock
    private AirportRepository airportRepository;

    @Mock
    private RoutesAcrossReplicas routes;

    @InjectMocks
    private ViewAirportRoutesUseCase viewAirportRoutes;

    @Test
    void ensureRoutesFromAndToTheAirportAreReturnedWithTheirVersion() {
        when(airportRepository.existsByIataCode(new IataCode("LIS"))).thenReturn(true);
        when(routes.findAll()).thenReturn(List.of(
                new Versioned<>(new Route("LIS", "OPO", 45, 300.0, 150), 7L),
                new Versioned<>(new Route("MAD", "LIS", 60, 640.0, 120), 2L),
                new Versioned<>(new Route("OPO", "MAD", 90, 850.0, 100), 1L)));

        List<RouteResponse> result = viewAirportRoutes.execute("LIS");

        assertEquals(2, result.size());
        assertEquals(7L, result.get(0).version());
        assertEquals("MAD", result.get(1).originIataCode());
    }

    @Test
    void ensureExceptionWhenAirportNotFound() {
        when(airportRepository.existsByIataCode(new IataCode("XXX"))).thenReturn(false);

        assertThrows(AirportNotFoundException.class, () -> viewAirportRoutes.execute("XXX"));
        verify(routes, never()).findAll();
    }
}
