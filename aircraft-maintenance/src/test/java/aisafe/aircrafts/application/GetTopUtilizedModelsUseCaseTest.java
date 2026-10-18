package aisafe.aircrafts.application;

import aisafe.aircrafts.application.dtos.TopUtilizedModelResponse;
import aisafe.aircrafts.domain.*;
import aisafe.flightcopies.application.FlightCopiesAcrossReplicas;
import aisafe.flightcopies.domain.FlightCopy;
import aisafe.flightcopies.domain.FlightCopyStatus;
import aisafe.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetTopUtilizedModelsUseCaseTest {

    @Mock
    private FlightCopiesAcrossReplicas flights;

    @Mock
    private AircraftAcrossReplicas aircraft;

    @InjectMocks
    private GetTopUtilizedModelsUseCase useCase;

    private static Aircraft aircraft(String registration, String modelName) {
        AircraftModel model = new AircraftModel(modelName, Manufacturer.AIRBUS, 26730.0, 6150.0, 833.0, null, 180);
        return new Aircraft(AircraftStatus.AVAILABLE, LocalDate.of(2020, 1, 1), model, new RegistrationNumber(registration), 150, 5000.0, List.of());
    }

    private static FlightCopy flight(String id, String registration, FlightCopyStatus status, int minutes) {
        OffsetDateTime departure = OffsetDateTime.of(2025, 1, 1, 8, 0, 0, 0, ZoneOffset.UTC);
        return new FlightCopy(id, departure, departure.plusMinutes(minutes), status, "OPO", "LIS", new RegistrationNumber(registration), 1);
    }

    private void fleet() {
        when(aircraft.findAll()).thenReturn(List.of(
                aircraft("CS-TAA", "A320"), aircraft("CS-TAB", "A320"), aircraft("CS-TBA", "B737")));
        when(flights.findAll()).thenReturn(List.of(
                flight("F1", "CS-TAA", FlightCopyStatus.COMPLETED, 90),
                flight("F2", "CS-TAB", FlightCopyStatus.COMPLETED, 60),
                flight("F3", "CS-TBA", FlightCopyStatus.COMPLETED, 300),
                flight("F4", "CS-TBA", FlightCopyStatus.SCHEDULED, 600),
                flight("F5", "CS-GON", FlightCopyStatus.COMPLETED, 600)));
    }

    @Test
    void ensureAssignmentsCountCompletedFlightsPerModel() {
        fleet();

        List<TopUtilizedModelResponse> result = useCase.execute("ASSIGNMENTS");

        assertEquals(List.of(new TopUtilizedModelResponse("A320", 2L), new TopUtilizedModelResponse("B737", 1L)), result);
    }

    @Test
    void ensureHoursSumCompletedFlightTimePerModelInWholeHours() {
        fleet();

        List<TopUtilizedModelResponse> result = useCase.execute("HOURS");

        assertEquals(List.of(new TopUtilizedModelResponse("B737", 5L), new TopUtilizedModelResponse("A320", 2L)), result);
    }

    @Test
    void ensureOnlyTheTopFiveModelsAreReturned() {
        List<Aircraft> planes = new ArrayList<>();
        List<FlightCopy> copies = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            String registration = "CS-TA" + (char) ('A' + i);
            planes.add(aircraft(registration, "M" + i));
            for (int f = 0; f < i; f++) {
                copies.add(flight("F" + i + "-" + f, registration, FlightCopyStatus.COMPLETED, 60));
            }
        }
        when(aircraft.findAll()).thenReturn(planes);
        when(flights.findAll()).thenReturn(copies);

        List<TopUtilizedModelResponse> result = useCase.execute("assignments");

        assertEquals(List.of("M7", "M6", "M5", "M4", "M3"), result.stream().map(TopUtilizedModelResponse::modelName).toList());
    }

    @Test
    void ensureWithInvalidCriteriaThrowsException() {
        assertThrows(DomainException.class, () -> useCase.execute("INVALID"));
        verifyNoInteractions(flights, aircraft);
    }
}
