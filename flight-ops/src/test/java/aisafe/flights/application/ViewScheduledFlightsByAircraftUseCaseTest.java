package aisafe.flights.application;

import aisafe.fleet.application.Fleet;
import aisafe.fleet.domain.AircraftInfo;
import aisafe.fleet.domain.AircraftNotFoundException;
import aisafe.flights.application.dtos.FlightResponse;
import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.IataCode;
import aisafe.flights.domain.RegistrationNumber;
import aisafe.flights.domain.ScheduledFlight;
import aisafe.shared.application.Versioned;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ViewScheduledFlightsByAircraftUseCaseTest {

    @Mock
    private FlightsAcrossReplicas flightsAcrossReplicas;

    @Mock
    private Fleet fleet;

    @InjectMocks
    private ViewScheduledFlightsByAircraftUseCase useCase;

    @Test
    void ensureReturnsFlights() {
        String aircraftId = "CS-TPA";
        RegistrationNumber registrationNumber = new RegistrationNumber(aircraftId);
        AircraftInfo aircraftInfo = new AircraftInfo(aircraftId, "A320", "AVAILABLE", 180, 5000.0, 3.5);
        when(fleet.findAircraft(aircraftId)).thenReturn(Optional.of(aircraftInfo));

        ScheduledFlight flight = new ScheduledFlight(
                1L,
                OffsetDateTime.now(),
                OffsetDateTime.now().plusHours(2),
                FlightStatus.SCHEDULED,
                new IataCode("OPO"),
                new IataCode("LIS"),
                registrationNumber,
                1L
        );

        when(flightsAcrossReplicas.findAll()).thenReturn(List.of(new Versioned<>(flight, 1L)));

        List<FlightResponse> result = useCase.execute(aircraftId);

        assertEquals(1, result.size());
        assertEquals(aircraftId, result.get(0).aircraftId());
    }

    @Test
    void ensureExceptionThrownWhenAircraftNotFound() {
        String aircraftId = "CS-TPA";
        when(fleet.findAircraft(aircraftId)).thenReturn(Optional.empty());

        assertThrows(AircraftNotFoundException.class, () -> useCase.execute(aircraftId));
        verify(flightsAcrossReplicas, never()).findAll();
    }
}
