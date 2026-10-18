package aisafe.aircrafts.application;

import aisafe.aircrafts.domain.*;
import aisafe.shared.domain.ResourceInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteAircraftModelUseCaseTest {

    @Mock
    private AircraftModelRepository aircraftModelRepository;

    @Mock
    private AircraftAcrossReplicas aircraftAcrossReplicas;

    @InjectMocks
    private DeleteAircraftModelUseCase deleteAircraftModelUseCase;

    private AircraftModel aircraftModel;

    @BeforeEach
    void setUp() {
        aircraftModel = new AircraftModel("A320", Manufacturer.AIRBUS, 26730.0, 6150.0, 833.0, null, 180);
    }

    private static Aircraft aircraftOf(String modelName) {
        AircraftModel model = new AircraftModel(modelName, Manufacturer.AIRBUS, 26730.0, 6150.0, 833.0, null, 180);
        return new Aircraft(AircraftStatus.AVAILABLE, LocalDate.of(2020, 1, 1), model, new RegistrationNumber("CS-TPA"), 150, 5000.0, List.of());
    }

    @Test
    void ensureModelIsDeletedSuccessfully() {
        when(aircraftModelRepository.findByModelName("A320")).thenReturn(Optional.of(aircraftModel));
        when(aircraftAcrossReplicas.findAll()).thenReturn(List.of(aircraftOf("B737")));

        assertDoesNotThrow(() -> deleteAircraftModelUseCase.execute("A320"));
        verify(aircraftModelRepository, times(1)).delete(aircraftModel);
    }

    @Test
    void ensureExceptionWhenModelNotFound() {
        when(aircraftModelRepository.findByModelName("NON-EXISTENT")).thenReturn(Optional.empty());

        assertThrows(AircraftModelNotFoundException.class, () -> deleteAircraftModelUseCase.execute("NON-EXISTENT"));
        verify(aircraftModelRepository, never()).delete(any());
    }

    @Test
    void ensureExceptionWhenModelIsInUse() {
        when(aircraftModelRepository.findByModelName("A320")).thenReturn(Optional.of(aircraftModel));
        // The aircraft using the model may be held by another replica
        when(aircraftAcrossReplicas.findAll()).thenReturn(List.of(aircraftOf("A320")));

        assertThrows(ResourceInUseException.class, () -> deleteAircraftModelUseCase.execute("A320"));
        verify(aircraftModelRepository, never()).delete(any());
    }
}
