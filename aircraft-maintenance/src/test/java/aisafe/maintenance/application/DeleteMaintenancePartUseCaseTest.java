package aisafe.maintenance.application;

import aisafe.shared.application.Versioned;
import aisafe.maintenance.domain.MaintenanceRecord;
import aisafe.maintenance.domain.MaintenanceStatus;
import aisafe.aircrafts.domain.RegistrationNumber;
import aisafe.maintenance.domain.MaintenanceComponent;
import aisafe.maintenance.domain.MaintenancePart;
import aisafe.maintenance.domain.MaintenancePartNotFoundException;
import aisafe.maintenance.domain.MaintenancePartRepository;
import aisafe.shared.domain.ResourceInUseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteMaintenancePartUseCaseTest {

    @Mock
    private MaintenancePartRepository maintenancePartRepository;

    @Mock
    private MaintenanceRecordsAcrossReplicas records;

    @InjectMocks
    private DeleteMaintenancePartUseCase deleteMaintenancePart;

    private MaintenancePart buildPart() {
        return new MaintenancePart("PN-001", "Engine Filter", "A filter", 10, 2, MaintenanceComponent.ENGINE);
    }

    @Test
    void ensureMaintenancePartIsDeletedSuccessfully() {
        MaintenancePart part = buildPart();
        when(maintenancePartRepository.findByPartNumber("PN-001")).thenReturn(Optional.of(part));
        when(records.findAll()).thenReturn(List.of());

        assertDoesNotThrow(() -> deleteMaintenancePart.execute("PN-001"));
        verify(maintenancePartRepository).delete(any(MaintenancePart.class));
    }

    @Test
    void ensureExceptionWhenMaintenancePartNotFound() {
        when(maintenancePartRepository.findByPartNumber("UNKNOWN")).thenReturn(Optional.empty());

        assertThrows(MaintenancePartNotFoundException.class, () -> deleteMaintenancePart.execute("UNKNOWN"));
        verify(maintenancePartRepository, never()).delete(any());
    }

    @Test
    void ensureExceptionWhenMaintenancePartIsInUse() {
        MaintenancePart part = buildPart();
        when(maintenancePartRepository.findByPartNumber("PN-001")).thenReturn(Optional.of(part));
        // The record using the part may be held by another replica
        when(records.findAll()).thenReturn(List.of(new Versioned<>(new MaintenanceRecord(UUID.randomUUID(), "Check",
                LocalDateTime.of(2025, 1, 1, 8, 0), 4, List.of(part), "", RecordsFixture.template("T1"), MaintenanceStatus.PLANNED,
                Set.of(MaintenanceComponent.ENGINE), new RegistrationNumber("CS-TPA"), BigDecimal.TEN, null), 0L)));

        assertThrows(ResourceInUseException.class, () -> deleteMaintenancePart.execute("PN-001"));
        verify(maintenancePartRepository, never()).delete(any());
    }
}
