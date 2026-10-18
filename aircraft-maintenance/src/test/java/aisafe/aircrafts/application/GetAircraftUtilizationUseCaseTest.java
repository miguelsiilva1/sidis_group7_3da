package aisafe.aircrafts.application;

import aisafe.aircrafts.application.dtos.GetAircraftUtilizationRequest;
import aisafe.aircrafts.application.dtos.UtilizationDataPointResponse;
import aisafe.aircrafts.domain.*;
import aisafe.flightcopies.application.FlightCopiesAcrossReplicas;
import aisafe.flightcopies.domain.FlightCopy;
import aisafe.flightcopies.domain.FlightCopyStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class GetAircraftUtilizationUseCaseTest {

    private AircraftAcrossReplicas aircraft;
    private FlightCopiesAcrossReplicas flights;
    private GetAircraftUtilizationUseCase useCase;

    @BeforeEach
    void setUp() {
        aircraft = Mockito.mock(AircraftAcrossReplicas.class);
        flights = Mockito.mock(FlightCopiesAcrossReplicas.class);
        useCase = new GetAircraftUtilizationUseCase(aircraft, flights);
        AircraftModel model = new AircraftModel("A320", Manufacturer.AIRBUS, 26730.0, 6150.0, 833.0, null, 180);
        when(aircraft.findAll()).thenReturn(List.of(new Aircraft(AircraftStatus.AVAILABLE, LocalDate.of(2020, 1, 1), model,
                new RegistrationNumber("CS-TPA"), 150, 5000.0, List.of())));
    }

    private static FlightCopy flight(String id, String registration, FlightCopyStatus status, OffsetDateTime departure, int minutes) {
        return new FlightCopy(id, departure, departure.plusMinutes(minutes), status, "OPO", "LIS", new RegistrationNumber(registration), 1);
    }

    @Test
    void ensureThrowsExceptionWhenAircraftDoesNotExist() {
        assertThrows(AircraftNotFoundException.class, () ->
                useCase.execute(new GetAircraftUtilizationRequest("XX-XXX", LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 2)))
        );
    }

    @Test
    void ensureReturnsDataPointsForValidRange() {
        LocalDate start = LocalDate.of(2023, 1, 1);
        LocalDate end = LocalDate.of(2023, 1, 2);
        when(flights.findAll()).thenReturn(List.of(
                flight("F1", "CS-TPA", FlightCopyStatus.COMPLETED, OffsetDateTime.of(2023, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC), 120)));

        List<UtilizationDataPointResponse> result = useCase.execute(new GetAircraftUtilizationRequest("CS-TPA", start, end));

        assertEquals(2, result.size());
        assertEquals(start, result.get(0).date());
        assertEquals(2.0, result.get(0).flightHours());
        assertEquals(2.0 / 24.0 * 100.0, result.get(0).utilizationRatePercentage());

        assertEquals(end, result.get(1).date());
        assertEquals(0.0, result.get(1).flightHours());
        assertEquals(0.0, result.get(1).utilizationRatePercentage());
    }

    @Test
    void ensureIgnoresFlightsOutsideTheRangeNotCompletedOrOfAnotherAircraft() {
        LocalDate start = LocalDate.of(2023, 1, 1);
        LocalDate end = LocalDate.of(2023, 1, 2);
        when(flights.findAll()).thenReturn(List.of(
                flight("F1", "CS-TPA", FlightCopyStatus.COMPLETED, OffsetDateTime.of(2023, 1, 3, 10, 0, 0, 0, ZoneOffset.UTC), 120),
                flight("F2", "CS-TPA", FlightCopyStatus.SCHEDULED, OffsetDateTime.of(2023, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC), 120),
                flight("F3", "CS-TPB", FlightCopyStatus.COMPLETED, OffsetDateTime.of(2023, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC), 120)));

        List<UtilizationDataPointResponse> result = useCase.execute(new GetAircraftUtilizationRequest("CS-TPA", start, end));

        assertEquals(2, result.size());
        assertEquals(0.0, result.get(0).flightHours());
        assertEquals(0.0, result.get(1).flightHours());
    }
}
