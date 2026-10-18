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
class SearchMaintenanceRecordsUseCaseTest {

    @Mock
    private MaintenanceRecordsAcrossReplicas records;

    @InjectMocks
    private SearchMaintenanceRecordsUseCase useCase;

    private static final LocalDateTime START = LocalDateTime.of(2025, 1, 1, 8, 0);

    private void recordsOfEveryReplica() {
        when(records.findAll()).thenReturn(List.of(
                record("CS-TPA", START.plusDays(2), MaintenanceStatus.PLANNED, null, BigDecimal.TEN, 4, MaintenanceComponent.ENGINE),
                record("CS-TPA", START, MaintenanceStatus.PLANNED, null, BigDecimal.TEN, 4, MaintenanceComponent.AIRFRAME),
                record("CS-TPB", START.plusDays(1), MaintenanceStatus.PLANNED, null, BigDecimal.TEN, 4, MaintenanceComponent.ENGINE),
                record("CS-TPB", START.plusDays(10), MaintenanceStatus.PLANNED, null, BigDecimal.TEN, 4, MaintenanceComponent.AIRFRAME)));
    }

    private static List<String> startDays(PaginatedResult<MaintenanceRecordResponse> result) {
        return result.data().stream().map(r -> r.aircraftRegistration() + "@" + r.startDate().getDayOfMonth()).toList();
    }

    @Test
    void ensureReturnsEmptyPageWhenNoRecordsMatch() {
        recordsOfEveryReplica();

        PaginatedResult<MaintenanceRecordResponse> result = useCase.execute(new RegistrationNumber("CS-XXX"), null, null, null, 0, 10);

        assertTrue(result.data().isEmpty());
        assertEquals(0L, result.totalElements());
    }

    @Test
    void ensureWithoutFiltersEveryRecordIsReturnedByStartDate() {
        recordsOfEveryReplica();

        PaginatedResult<MaintenanceRecordResponse> result = useCase.execute(null, null, null, null, 0, 10);

        assertEquals(List.of("CS-TPA@1", "CS-TPB@2", "CS-TPA@3", "CS-TPB@11"), startDays(result));
    }

    @Test
    void ensureFiltersByAircraftRegistration() {
        recordsOfEveryReplica();

        PaginatedResult<MaintenanceRecordResponse> result = useCase.execute(new RegistrationNumber("CS-TPB"), null, null, null, 0, 10);

        assertEquals(List.of("CS-TPB@2", "CS-TPB@11"), startDays(result));
    }

    @Test
    void ensureFiltersByDateRangeWithInclusiveLimits() {
        recordsOfEveryReplica();

        PaginatedResult<MaintenanceRecordResponse> result = useCase.execute(null, START.plusDays(1), START.plusDays(2), null, 0, 10);

        assertEquals(List.of("CS-TPB@2", "CS-TPA@3"), startDays(result));
    }

    @Test
    void ensureFiltersByComponent() {
        recordsOfEveryReplica();

        PaginatedResult<MaintenanceRecordResponse> result = useCase.execute(null, null, null, MaintenanceComponent.AIRFRAME, 0, 10);

        assertEquals(List.of("CS-TPA@1", "CS-TPB@11"), startDays(result));
    }

    @Test
    void ensureAllFiltersAreAppliedTogether() {
        recordsOfEveryReplica();

        PaginatedResult<MaintenanceRecordResponse> result = useCase.execute(new RegistrationNumber("CS-TPA"), START, START.plusDays(5),
                MaintenanceComponent.ENGINE, 0, 10);

        assertEquals(List.of("CS-TPA@3"), startDays(result));
    }

    @Test
    void ensurePageIsCutAfterTheMatchesOfEveryReplicaAreTogether() {
        recordsOfEveryReplica();

        PaginatedResult<MaintenanceRecordResponse> page = useCase.execute(null, null, null, null, 1, 3);

        assertEquals(4L, page.totalElements());
        assertEquals(List.of("CS-TPB@11"), startDays(page));
    }

    @Test
    void ensureReturnsMappedRecordsSuccessfully() {
        Versioned<MaintenanceRecord> stored = new Versioned<>(record("CS-TPA", START).value(), 2L);
        when(records.findAll()).thenReturn(List.of(stored));

        MaintenanceRecordResponse response = useCase.execute(null, null, null, null, 0, 10).data().get(0);

        MaintenanceRecord record = stored.value();
        assertEquals(record.getRecordId(), response.id());
        assertEquals("Maintenance", response.description());
        assertEquals(START, response.startDate());
        assertEquals(4, response.expectedDuration());
        assertEquals("notes", response.notes());
        assertEquals(List.of("P001"), response.partNumbers());
        assertEquals("Template1", response.templateName());
        assertEquals("PLANNED", response.status());
        assertEquals("CS-TPA", response.aircraftRegistration());
        assertEquals(2L, response.version());
        assertEquals(Set.of("ENGINE"), response.components());
        assertEquals(BigDecimal.TEN, response.cost());
    }
}
