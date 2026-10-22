package aisafe.flights.application;

import aisafe.flights.application.dtos.FlightUtilizationResponse;
import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.IataCode;
import aisafe.flights.domain.InvalidFlightDateRangeException;
import aisafe.flights.domain.RegistrationNumber;
import aisafe.flights.domain.ScheduledFlight;
import aisafe.shared.application.Versioned;
import aisafe.shared.domain.PaginatedResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerateFlightUtilizationReportUseCaseTest {

    @Mock
    private FlightsAcrossReplicas flightsAcrossReplicas;

    private GenerateFlightUtilizationReportUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GenerateFlightUtilizationReportUseCase(flightsAcrossReplicas);
    }

    private ScheduledFlight completedFlight(Long id, String origin, String destination, OffsetDateTime dep, OffsetDateTime arr) {
        return new ScheduledFlight(
                id,
                dep,
                arr,
                FlightStatus.COMPLETED,
                new IataCode(origin),
                new IataCode(destination),
                new RegistrationNumber("CS-TPA"),
                1L
        );
    }

    @Test
    void ensureReportIsGenerated() {
        OffsetDateTime start = OffsetDateTime.now().minusDays(10);
        OffsetDateTime end = OffsetDateTime.now();

        ScheduledFlight f1 = completedFlight(1L, "OPO", "LIS", start.plusDays(1), start.plusDays(1).plusHours(2));
        ScheduledFlight f2 = completedFlight(2L, "OPO", "LIS", start.plusDays(2), start.plusDays(2).plusHours(2));
        ScheduledFlight f3 = completedFlight(3L, "LIS", "MAD", start.plusDays(3), start.plusDays(3).plusHours(2));

        when(flightsAcrossReplicas.findAll()).thenReturn(List.of(
                new Versioned<>(f1, 1L),
                new Versioned<>(f2, 1L),
                new Versioned<>(f3, 1L)
        ));

        PaginatedResult<FlightUtilizationResponse> result = useCase.execute(start, end, 0, 20);

        assertEquals(2L, result.totalElements());
        assertEquals(2, result.data().size());

        FlightUtilizationResponse top = result.data().get(0);
        assertEquals("OPO", top.origin());
        assertEquals("LIS", top.destination());
        assertEquals(2L, top.count());

        FlightUtilizationResponse second = result.data().get(1);
        assertEquals("LIS", second.origin());
        assertEquals("MAD", second.destination());
        assertEquals(1L, second.count());
    }

    @Test
    void ensureFailsWhenStartDateAfterEndDate() {
        OffsetDateTime start = OffsetDateTime.now();
        OffsetDateTime end = start.minusDays(1);

        assertThrows(InvalidFlightDateRangeException.class, () -> useCase.execute(start, end, 0, 20));
    }

    @Test
    void ensureReportIsGeneratedWithDefaultPageAndSize() {
        OffsetDateTime start = OffsetDateTime.now().minusDays(10);
        OffsetDateTime end = OffsetDateTime.now();

        ScheduledFlight f1 = completedFlight(1L, "OPO", "LIS", start.plusDays(1), start.plusDays(1).plusHours(2));
        when(flightsAcrossReplicas.findAll()).thenReturn(List.of(new Versioned<>(f1, 1L)));

        PaginatedResult<FlightUtilizationResponse> result = useCase.execute(start, end, null, null);

        assertEquals(1L, result.totalElements());
    }

    @Test
    void ensureReportIsGeneratedWithNullDates() {
        OffsetDateTime now = OffsetDateTime.now();
        ScheduledFlight f1 = completedFlight(1L, "OPO", "LIS", now.minusDays(1), now.minusDays(1).plusHours(2));
        when(flightsAcrossReplicas.findAll()).thenReturn(List.of(new Versioned<>(f1, 1L)));

        PaginatedResult<FlightUtilizationResponse> result = useCase.execute(null, null, 0, 20);

        assertEquals(1L, result.totalElements());
    }

    @Test
    void ensureReportIsGeneratedWithNullEndDate() {
        OffsetDateTime start = OffsetDateTime.now().minusDays(10);
        ScheduledFlight f1 = completedFlight(1L, "OPO", "LIS", start.plusDays(1), start.plusDays(1).plusHours(2));
        when(flightsAcrossReplicas.findAll()).thenReturn(List.of(new Versioned<>(f1, 1L)));

        PaginatedResult<FlightUtilizationResponse> result = useCase.execute(start, null, 0, 20);

        assertEquals(1L, result.totalElements());
    }

    @Test
    void ensureReportIsGeneratedWithNullStartDate() {
        OffsetDateTime end = OffsetDateTime.now();
        ScheduledFlight f1 = completedFlight(1L, "OPO", "LIS", end.minusDays(1), end.minusDays(1).plusHours(2));
        when(flightsAcrossReplicas.findAll()).thenReturn(List.of(new Versioned<>(f1, 1L)));

        PaginatedResult<FlightUtilizationResponse> result = useCase.execute(null, end, 0, 20);

        assertEquals(1L, result.totalElements());
    }
}
