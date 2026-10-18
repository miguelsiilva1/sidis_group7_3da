package aisafe.aircrafts.application;

import aisafe.aircrafts.application.dtos.AircraftModelForServicesResponse;
import aisafe.aircrafts.domain.AircraftModel;
import aisafe.aircrafts.domain.AircraftModelNotFoundException;
import aisafe.aircrafts.domain.AircraftModelRepository;
import aisafe.aircrafts.domain.Manufacturer;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ViewAircraftModelForServicesUseCaseTest {

    private final AircraftModelRepository repository = mock(AircraftModelRepository.class);
    private final ViewAircraftModelForServicesUseCase useCase = new ViewAircraftModelForServicesUseCase(repository);

    @Test
    void ensureExistingModelIsReturnedWithItsManufacturer() {
        when(repository.findByModelName("Airbus A320neo")).thenReturn(Optional.of(
                new AircraftModel("Airbus A320neo", Manufacturer.AIRBUS, 24210.0, 6570.0, 828.0, null, 240)));

        AircraftModelForServicesResponse response = useCase.execute("Airbus A320neo");

        assertEquals("Airbus A320neo", response.modelName());
        assertEquals(Manufacturer.AIRBUS, response.manufacturer());
    }

    @Test
    void ensureUnknownModelIsNotFound() {
        when(repository.findByModelName("Unknown")).thenReturn(Optional.empty());

        assertThrows(AircraftModelNotFoundException.class, () -> useCase.execute("Unknown"));
    }
}
