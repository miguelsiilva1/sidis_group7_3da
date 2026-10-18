package aisafe.maintenance.application;

import aisafe.maintenance.application.dtos.AverageTurnaroundByModelResponse;
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
class ViewAverageMaintenanceTurnaroundUseCaseTest {

    @Mock
    private MaintenanceRecordsAcrossReplicas records;

    @Mock
    private AircraftAcrossReplicas aircraft;

    @Mock
    private AircraftModelRepository aircraftModelRepository;

    @InjectMocks
    private ViewAverageMaintenanceTurnaroundUseCase useCase;

    private static final LocalDateTime START = LocalDateTime.of(2025, 1, 1, 8, 0);

    @Test
    void ensureAverageHoursOfCompletedRecordsOfTheModelAreReturned() {
        when(aircraftModelRepository.existsByModelName("A320")).thenReturn(true);
        when(aircraft.findAll()).thenReturn(List.of(aircraft("CS-TPA", "A320"), aircraft("CS-TPB", "A320"), aircraft("CS-TBO", "B737")));
        when(records.findAll()).thenReturn(List.of(
                record("CS-TPA", START, MaintenanceStatus.COMPLETED, START.plusHours(4), BigDecimal.TEN, 4, MaintenanceComponent.ENGINE),
                record("CS-TPB", START, MaintenanceStatus.COMPLETED, START.plusHours(8), BigDecimal.TEN, 4, MaintenanceComponent.ENGINE),
                record("CS-TPB", START, MaintenanceStatus.IN_PROGRESS, null, BigDecimal.TEN, 4, MaintenanceComponent.ENGINE),
                record("CS-TBO", START, MaintenanceStatus.COMPLETED, START.plusHours(100), BigDecimal.TEN, 4, MaintenanceComponent.ENGINE)));

        AverageTurnaroundByModelResponse response = useCase.execute("A320");

        assertEquals("A320", response.modelName());
        assertEquals(6.0, response.averageHours());
    }

    @Test
    void ensureZeroAverageWhenNoAircraftOfModelExist() {
        when(aircraftModelRepository.existsByModelName("A320")).thenReturn(true);
        when(aircraft.findAll()).thenReturn(List.of());
        when(records.findAll()).thenReturn(List.of(
                record("CS-TBO", START, MaintenanceStatus.COMPLETED, START.plusHours(100), BigDecimal.TEN, 4, MaintenanceComponent.ENGINE)));

        AverageTurnaroundByModelResponse response = useCase.execute("A320");

        assertEquals(0.0, response.averageHours());
    }

    @Test
    void ensureExceptionWhenModelNotFound() {
        when(aircraftModelRepository.existsByModelName("X")).thenReturn(false);

        assertThrows(AircraftModelNotFoundException.class, () -> useCase.execute("X"));
    }
}
