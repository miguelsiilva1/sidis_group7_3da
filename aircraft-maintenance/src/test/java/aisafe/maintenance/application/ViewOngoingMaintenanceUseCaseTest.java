package aisafe.maintenance.application;

import aisafe.maintenance.application.dtos.MaintenanceRecordResponse;
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
import java.util.Set;

import static aisafe.maintenance.application.RecordsFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ViewOngoingMaintenanceUseCaseTest {

    @Mock
    private MaintenanceRecordsAcrossReplicas records;

    @InjectMocks
    private ViewOngoingMaintenanceUseCase useCase;

    private static final LocalDateTime START = LocalDateTime.of(2025, 1, 1, 8, 0);

    private static Versioned<MaintenanceRecord> withStatus(String registration, LocalDateTime start, MaintenanceStatus status) {
        return record(registration, start, status, null, BigDecimal.TEN, 4, MaintenanceComponent.ENGINE);
    }

    @Test
    void ensureReturnsEmptyPageWhenNoOngoingRecords() {
        when(records.findAll()).thenReturn(List.of(withStatus("CS-TPA", START, MaintenanceStatus.PLANNED)));

        PaginatedResult<MaintenanceRecordResponse> result = useCase.execute(0, 10);

        assertTrue(result.data().isEmpty());
        assertEquals(0L, result.totalElements());
    }

    @Test
    void ensureReturnsOngoingRecordsOfEveryReplicaMostRecentFirst() {
        Versioned<MaintenanceRecord> older = withStatus("CS-TPA", START, MaintenanceStatus.IN_PROGRESS);
        Versioned<MaintenanceRecord> newer = new Versioned<>(withStatus("CS-TPB", START.plusDays(2), MaintenanceStatus.IN_PROGRESS).value(), 3L);
        when(records.findAll()).thenReturn(List.of(older, withStatus("CS-TPC", START, MaintenanceStatus.COMPLETED), newer));

        PaginatedResult<MaintenanceRecordResponse> result = useCase.execute(0, 10);

        assertEquals(2L, result.totalElements());
        assertEquals(List.of("CS-TPB", "CS-TPA"), result.data().stream().map(MaintenanceRecordResponse::aircraftRegistration).toList());
        MaintenanceRecordResponse first = result.data().get(0);
        assertEquals("IN_PROGRESS", first.status());
        assertEquals(3L, first.version(), "the version comes from the replica that holds the record");
        assertEquals(Set.of("ENGINE"), first.components());
        assertEquals(List.of("P001"), first.partNumbers());
    }

    @Test
    void ensurePageIsCutAfterTheRecordsOfEveryReplicaAreTogether() {
        when(records.findAll()).thenReturn(List.of(
                withStatus("CS-TPA", START, MaintenanceStatus.IN_PROGRESS),
                withStatus("CS-TPB", START.plusDays(1), MaintenanceStatus.IN_PROGRESS),
                withStatus("CS-TPC", START.plusDays(2), MaintenanceStatus.IN_PROGRESS)));

        PaginatedResult<MaintenanceRecordResponse> page = useCase.execute(1, 2);

        assertEquals(3L, page.totalElements());
        assertEquals(List.of("CS-TPA"), page.data().stream().map(MaintenanceRecordResponse::aircraftRegistration).toList());
    }
}
