package aisafe.maintenance.application;

import aisafe.maintenance.application.dtos.MaintenanceCostByAircraftResponse;
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
class ViewMaintenanceCostByAircraftUseCaseTest {

    @Mock
    private MaintenanceRecordsAcrossReplicas records;

    @Mock
    private AircraftAcrossReplicas aircraft;

    @InjectMocks
    private ViewMaintenanceCostByAircraftUseCase useCase;

    private static final LocalDateTime START = LocalDateTime.of(2025, 1, 1, 8, 0);

    @Test
    void ensureCostsOfTheAircraftRecordsAreSummed() {
        when(aircraft.findAll()).thenReturn(List.of(aircraft("CS-TPA", "A320")));
        when(records.findAll()).thenReturn(List.of(
                record("CS-TPA", START, MaintenanceStatus.PLANNED, null, BigDecimal.valueOf(1000), 4, MaintenanceComponent.ENGINE),
                record("CS-TPA", START.plusDays(1), MaintenanceStatus.PLANNED, null, BigDecimal.valueOf(500), 4, MaintenanceComponent.ENGINE),
                record("CS-TPB", START, MaintenanceStatus.PLANNED, null, BigDecimal.valueOf(9000), 4, MaintenanceComponent.ENGINE)));

        MaintenanceCostByAircraftResponse response = useCase.execute("CS-TPA");

        assertEquals("CS-TPA", response.aircraftRegistration());
        assertEquals(BigDecimal.valueOf(1500), response.totalCost());
    }

    @Test
    void ensureZeroCostWhenNoRecordsExist() {
        when(aircraft.findAll()).thenReturn(List.of(aircraft("CS-TPA", "A320")));
        when(records.findAll()).thenReturn(List.of());

        MaintenanceCostByAircraftResponse response = useCase.execute("CS-TPA");

        assertEquals(BigDecimal.ZERO, response.totalCost());
    }

    @Test
    void ensureExceptionWhenAircraftNotFound() {
        when(aircraft.findAll()).thenReturn(List.of(aircraft("CS-TPB", "A320")));

        assertThrows(AircraftNotFoundException.class, () -> useCase.execute("CS-TPA"));
    }
}
