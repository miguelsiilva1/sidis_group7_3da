package aisafe.routes.application;

import aisafe.airports.domain.AirportNotFoundException;
import aisafe.airports.domain.AirportRepository;
import aisafe.airports.domain.IataCode;
import aisafe.routes.domain.Route;
import aisafe.shared.application.Versioned;
import aisafe.shared.domain.PaginatedResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListRoutesFromAirportUseCaseTest {

    @Mock
    private RoutesAcrossReplicas routes;

    @Mock
    private AirportRepository airportRepository;

    @InjectMocks
    private ListRoutesFromAirportUseCase listRoutesFromAirport;

    @Test
    void ensureRoutesReturnedForExistingAirport() {
        Route route = new Route("OPO", "LIS", 45, 300.0, 150);
        when(airportRepository.existsByIataCode(new IataCode("OPO"))).thenReturn(true);
        when(routes.findAll()).thenReturn(List.of(new Versioned<>(new Route("LIS", "OPO", 45, 300.0, 150), 1L),
                new Versioned<>(route, 3L)));

        PaginatedResult<aisafe.routes.application.dtos.RouteResponse> result = listRoutesFromAirport.execute("OPO", 0, 20);

        assertEquals(1L, result.totalElements());
        assertEquals("LIS", result.data().get(0).destinationIataCode());
        assertEquals(3L, result.data().get(0).version());
    }

    @Test
    void ensureExceptionWhenAirportNotFound() {
        when(airportRepository.existsByIataCode(new IataCode("XXX"))).thenReturn(false);

        assertThrows(AirportNotFoundException.class, () -> listRoutesFromAirport.execute("XXX", 0, 20));
        verify(routes, never()).findAll();
    }
}
