package aisafe.maintenance.application;

import aisafe.maintenance.application.dtos.ViewTotalMaintenanceHoursInFleetResponse;
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
class ViewTotalMaintenanceHoursInFleetUseCaseTest {

    @Mock
    private MaintenanceRecordsAcrossReplicas records;

    @InjectMocks
    private ViewTotalMaintenanceHoursInFleetUseCase viewTotalHours;

    @Test
    void ensureEmptyFleetReturnsZeroHours() {
        when(records.findAll()).thenReturn(List.of());

        ViewTotalMaintenanceHoursInFleetResponse result = viewTotalHours.execute();

        assertEquals(0, result.totalHours());
    }

    @Test
    void ensureExpectedDurationsOfEveryReplicaAreSummed() {
        LocalDateTime start = LocalDateTime.of(2025, 1, 1, 8, 0);
        when(records.findAll()).thenReturn(List.of(
                record("CS-TPA", start, MaintenanceStatus.PLANNED, null, BigDecimal.TEN, 4, MaintenanceComponent.ENGINE),
                record("CS-TPB", start, MaintenanceStatus.COMPLETED, start.plusHours(9), BigDecimal.TEN, 8, MaintenanceComponent.ENGINE)));

        ViewTotalMaintenanceHoursInFleetResponse result = viewTotalHours.execute();

        assertEquals(12, result.totalHours());
    }
}
