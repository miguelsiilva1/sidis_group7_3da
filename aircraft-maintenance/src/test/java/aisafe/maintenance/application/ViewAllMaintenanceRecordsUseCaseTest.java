package aisafe.maintenance.application;

import aisafe.maintenance.application.dtos.ViewAllMaintenanceRecordsResponse;
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
class ViewAllMaintenanceRecordsUseCaseTest {

    @Mock
    private MaintenanceRecordsAcrossReplicas records;

    @Mock
    private AircraftAcrossReplicas aircraft;

    @InjectMocks
    private ViewAllMaintenanceRecordsUseCase useCase;

    private static final LocalDateTime START = LocalDateTime.of(2025, 1, 1, 8, 0);

    @Test
    void ensureRecordsOfTheAircraftFromEveryReplicaAreReturnedByStartDate() {
        when(aircraft.findAll()).thenReturn(List.of(aircraft("CS-TPA", "A320")));
        when(records.findAll()).thenReturn(List.of(
                record("CS-TPA", START.plusDays(3)),
                record("CS-TPB", START),
                record("CS-TPA", START.plusDays(1))));

        PaginatedResult<ViewAllMaintenanceRecordsResponse> result = useCase.execute(new RegistrationNumber("CS-TPA"), 0, 10);

        assertEquals(2L, result.totalElements());
        assertEquals(List.of(START.plusDays(1), START.plusDays(3)),
                result.data().stream().map(ViewAllMaintenanceRecordsResponse::startDate).toList());
        ViewAllMaintenanceRecordsResponse first = result.data().get(0);
        assertEquals(List.of("P001"), first.partNumbers());
        assertEquals("Template1", first.name());
        assertEquals(4, first.expectedDuration());
        assertEquals(MaintenanceStatus.PLANNED, first.status());
        assertEquals("notes", first.notes());
        assertEquals("CS-TPA", first.number());
        assertEquals(Set.of("ENGINE"), first.components());
    }

    @Test
    void ensurePageIsCutAfterTheRecordsOfEveryReplicaAreTogether() {
        when(aircraft.findAll()).thenReturn(List.of(aircraft("CS-TPA", "A320")));
        when(records.findAll()).thenReturn(List.of(record("CS-TPA", START), record("CS-TPA", START.plusDays(1)), record("CS-TPA", START.plusDays(2))));

        PaginatedResult<ViewAllMaintenanceRecordsResponse> page = useCase.execute(new RegistrationNumber("CS-TPA"), 1, 2);

        assertEquals(3L, page.totalElements());
        assertEquals(List.of(START.plusDays(2)), page.data().stream().map(ViewAllMaintenanceRecordsResponse::startDate).toList());
    }

    @Test
    void ensureExceptionWhenAircraftNotFound() {
        when(aircraft.findAll()).thenReturn(List.of(aircraft("CS-TPB", "A320")));

        assertThrows(AircraftNotFoundException.class, () -> useCase.execute(new RegistrationNumber("CS-TPA"), 0, 10));
        verify(records, never()).findAll();
    }
}
