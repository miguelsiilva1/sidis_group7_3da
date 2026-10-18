package aisafe.maintenance.application;

import aisafe.maintenance.domain.MaintenanceStatus;
import aisafe.maintenance.domain.MaintenanceComponent;
import aisafe.aircrafts.domain.ModelName;
import aisafe.maintenance.domain.MaintenanceTemplate;
import aisafe.maintenance.domain.MaintenanceTemplateNotFoundException;
import aisafe.maintenance.domain.MaintenanceTemplateRepository;
import aisafe.maintenance.domain.MaintenanceType;
import aisafe.shared.domain.ResourceInUseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteMaintenanceTemplateUseCaseTest {

    @Mock
    private MaintenanceTemplateRepository maintenanceTemplateRepository;

    @Mock
    private MaintenanceRecordsAcrossReplicas records;

    @InjectMocks
    private DeleteMaintenanceTemplateUseCase deleteMaintenanceTemplate;

    private MaintenanceTemplate buildTemplate() {
        return new MaintenanceTemplate("Engine Check", MaintenanceType.INSPECTION,
                List.of(new ModelName("ModelA")), List.of("Check oil"), 500, 90);
    }

    @Test
    void ensureMaintenanceTemplateIsDeletedSuccessfully() {
        MaintenanceTemplate template = buildTemplate();
        when(maintenanceTemplateRepository.findByName("Engine Check")).thenReturn(Optional.of(template));
        when(records.findAll()).thenReturn(List.of(RecordsFixture.record("CS-TPA", "Other Check", LocalDateTime.of(2025, 1, 1, 8, 0),
                MaintenanceStatus.PLANNED, null, BigDecimal.TEN, 4, MaintenanceComponent.ENGINE)));

        assertDoesNotThrow(() -> deleteMaintenanceTemplate.execute("Engine Check"));
        verify(maintenanceTemplateRepository).delete(any(MaintenanceTemplate.class));
    }

    @Test
    void ensureExceptionWhenMaintenanceTemplateNotFound() {
        when(maintenanceTemplateRepository.findByName("Unknown")).thenReturn(Optional.empty());

        assertThrows(MaintenanceTemplateNotFoundException.class, () -> deleteMaintenanceTemplate.execute("Unknown"));
        verify(maintenanceTemplateRepository, never()).delete(any());
    }

    @Test
    void ensureExceptionWhenMaintenanceTemplateIsInUse() {
        MaintenanceTemplate template = buildTemplate();
        when(maintenanceTemplateRepository.findByName("Engine Check")).thenReturn(Optional.of(template));
        // The record using the template may be held by another replica
        when(records.findAll()).thenReturn(List.of(RecordsFixture.record("CS-TPA", "Engine Check", LocalDateTime.of(2025, 1, 1, 8, 0),
                MaintenanceStatus.PLANNED, null, BigDecimal.TEN, 4, MaintenanceComponent.ENGINE)));

        assertThrows(ResourceInUseException.class, () -> deleteMaintenanceTemplate.execute("Engine Check"));
        verify(maintenanceTemplateRepository, never()).delete(any());
    }
}
