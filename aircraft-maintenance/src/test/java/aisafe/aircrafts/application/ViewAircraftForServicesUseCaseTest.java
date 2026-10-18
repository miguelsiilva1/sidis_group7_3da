package aisafe.aircrafts.application;

import aisafe.aircrafts.application.dtos.AircraftForServicesResponse;
import aisafe.aircrafts.application.dtos.ViewAircraftDetailsRequest;
import aisafe.aircrafts.domain.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ViewAircraftForServicesUseCaseTest {

    private final AircraftRepository repository = mock(AircraftRepository.class);
    private final ViewAircraftForServicesUseCase useCase = new ViewAircraftForServicesUseCase(repository);

    @Test
    void ensureAircraftDataNeededByOtherServicesIsReturned() {
        AircraftModel model = new AircraftModel("A320", Manufacturer.AIRBUS, 26730.0, 6150.0, 833.0, null, 180);
        Aircraft aircraft = new Aircraft(AircraftStatus.UNDER_MAINTENANCE, LocalDate.of(2020, 1, 1), model,
                new RegistrationNumber("CS-TPA"), 150, 5000.0, List.of());
        when(repository.findByRegistrationNumber(new RegistrationNumber("CS-TPA"))).thenReturn(Optional.of(aircraft));

        AircraftForServicesResponse response = useCase.execute(new ViewAircraftDetailsRequest(new RegistrationNumber("CS-TPA")));

        assertEquals("CS-TPA", response.registrationNumber());
        assertEquals("A320", response.model());
        assertEquals(AircraftStatus.UNDER_MAINTENANCE, response.status());
        assertEquals(150, response.seatCapacity());
        assertEquals(5000.0, response.range());
        assertEquals(aircraft.getFuelConsumptionPerDistanceUnit(), response.fuelConsumptionPerDistanceUnit());
    }

    @Test
    void ensureUnknownAircraftThrowsNotFound() {
        when(repository.findByRegistrationNumber(any())).thenReturn(Optional.empty());

        assertThrows(AircraftNotFoundException.class,
                () -> useCase.execute(new ViewAircraftDetailsRequest(new RegistrationNumber("CS-XXX"))));
    }
}
