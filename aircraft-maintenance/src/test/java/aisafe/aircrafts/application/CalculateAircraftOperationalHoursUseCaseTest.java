package aisafe.aircrafts.application;

import aisafe.aircrafts.application.dtos.CalculateAircraftOperationalHoursRequest;
import aisafe.aircrafts.application.dtos.AircraftOperationalHoursResponse;
import aisafe.aircrafts.domain.*;
import aisafe.flightcopies.application.FlightCopiesAcrossReplicas;
import aisafe.flightcopies.domain.FlightCopy;
import aisafe.flightcopies.domain.FlightCopyStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalculateAircraftOperationalHoursUseCaseTest {

    @Mock
    private FlightCopiesAcrossReplicas flights;

    @Mock
    private AircraftAcrossReplicas aircraft;

    @InjectMocks
    private CalculateAircraftOperationalHoursUseCase useCase;

    private static Aircraft aircraft(String registration) {
        AircraftModel model = new AircraftModel("A320", Manufacturer.AIRBUS, 26730.0, 6150.0, 833.0, null, 180);
        return new Aircraft(AircraftStatus.AVAILABLE, LocalDate.of(2020, 1, 1), model, new RegistrationNumber(registration), 150, 5000.0, List.of());
    }

    private static FlightCopy flight(String id, String registration, FlightCopyStatus status, int minutes) {
        OffsetDateTime departure = OffsetDateTime.of(2025, 1, 1, 8, 0, 0, 0, ZoneOffset.UTC);
        return new FlightCopy(id, departure, departure.plusMinutes(minutes), status, "OPO", "LIS", new RegistrationNumber(registration), 1);
    }

    @Test
    void ensureReturnsHoursOfCompletedFlightsOfTheAircraftOnly() {
        when(aircraft.findAll()).thenReturn(List.of(aircraft("CS-TKA")));
        when(flights.findAll()).thenReturn(List.of(
                flight("F1", "CS-TKA", FlightCopyStatus.COMPLETED, 600),
                flight("F2", "CS-TKA", FlightCopyStatus.COMPLETED, 330),
                flight("F3", "CS-TKA", FlightCopyStatus.SCHEDULED, 120),
                flight("F4", "CS-TKB", FlightCopyStatus.COMPLETED, 120)));

        AircraftOperationalHoursResponse response = useCase.execute(new CalculateAircraftOperationalHoursRequest(new RegistrationNumber("CS-TKA")));

        assertEquals("CS-TKA", response.registrationNumber());
        assertEquals(15.5, response.totalOperationalHours());
    }

    @Test
    void ensureReturnsZeroWithoutCompletedFlights() {
        when(aircraft.findAll()).thenReturn(List.of(aircraft("CS-TKA")));
        when(flights.findAll()).thenReturn(List.of());

        AircraftOperationalHoursResponse response = useCase.execute(new CalculateAircraftOperationalHoursRequest(new RegistrationNumber("CS-TKA")));

        assertEquals("CS-TKA", response.registrationNumber());
        assertEquals(0.0, response.totalOperationalHours());
    }

    @Test
    void ensureThrowsExceptionWhenAircraftNotFound() {
        when(aircraft.findAll()).thenReturn(List.of(aircraft("CS-TKA")));

        assertThrows(AircraftNotFoundException.class,
                () -> useCase.execute(new CalculateAircraftOperationalHoursRequest(new RegistrationNumber("CS-XXX"))));
        verify(flights, never()).findAll();
    }
}
