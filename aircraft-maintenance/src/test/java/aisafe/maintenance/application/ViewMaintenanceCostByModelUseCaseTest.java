package aisafe.maintenance.application;

import aisafe.maintenance.application.dtos.MaintenanceCostByModelResponse;
import aisafe.aircrafts.application.AircraftAcrossReplicas;
import aisafe.aircrafts.domain.*;
import aisafe.maintenance.domain.*;
import aisafe.shared.application.Versioned;
import aisafe.shared.domain.PaginatedResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static aisafe.maintenance.application.RecordsFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ViewMaintenanceCostByModelUseCaseTest {

    @Mock
    private MaintenanceRecordsAcrossReplicas records;

    @Mock
    private AircraftAcrossReplicas aircraft;

    @Mock
    private AircraftModelRepository aircraftModelRepository;

    @InjectMocks
    private ViewMaintenanceCostByModelUseCase useCase;

    private static final LocalDateTime START = LocalDateTime.of(2025, 1, 1, 8, 0);

    @Test
    void ensureCostIsAggregatedAcrossAllAircraftOfModel() {
        when(aircraftModelRepository.existsByModelName("A320")).thenReturn(true);
        when(aircraft.findAll()).thenReturn(List.of(aircraft("CS-TPA", "A320"), aircraft("CS-TPB", "A320"), aircraft("CS-TBO", "B737")));
        when(records.findAll()).thenReturn(List.of(
                record("CS-TPA", START, MaintenanceStatus.PLANNED, null, BigDecimal.valueOf(1000), 4, MaintenanceComponent.ENGINE),
                record("CS-TPB", START, MaintenanceStatus.PLANNED, null, BigDecimal.valueOf(2000), 4, MaintenanceComponent.ENGINE),
                record("CS-TBO", START, MaintenanceStatus.PLANNED, null, BigDecimal.valueOf(9000), 4, MaintenanceComponent.ENGINE)));

        MaintenanceCostByModelResponse response = useCase.execute("A320");

        assertEquals("A320", response.modelName());
        assertEquals(BigDecimal.valueOf(3000), response.totalCost());
    }

    @Test
    void ensureZeroCostWhenNoAircraftOfModelExist() {
        when(aircraftModelRepository.existsByModelName("A320")).thenReturn(true);
        when(aircraft.findAll()).thenReturn(List.of(aircraft("CS-TBO", "B737")));

        MaintenanceCostByModelResponse response = useCase.execute("A320");

        assertEquals(BigDecimal.ZERO, response.totalCost());
        verify(records, never()).findAll();
    }

    @Test
    void ensureExceptionWhenModelNotFound() {
        when(aircraftModelRepository.existsByModelName("X")).thenReturn(false);

        assertThrows(AircraftModelNotFoundException.class, () -> useCase.execute("X"));
    }
}
