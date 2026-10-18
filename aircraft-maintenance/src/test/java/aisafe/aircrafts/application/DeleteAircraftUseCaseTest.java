package aisafe.aircrafts.application;

import aisafe.aircrafts.domain.*;
import aisafe.flightcopies.application.FlightCopiesAcrossReplicas;
import aisafe.flightcopies.domain.FlightCopy;
import aisafe.flightcopies.domain.FlightCopyStatus;
import aisafe.maintenance.application.MaintenanceRecordsAcrossReplicas;
import aisafe.maintenance.domain.*;
import aisafe.shared.application.Versioned;
import aisafe.shared.domain.ResourceInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteAircraftUseCaseTest {

    @Mock
    private AircraftRepository aircraftRepository;

    @Mock
    private MaintenanceRecordsAcrossReplicas records;

    @Mock
    private FlightCopiesAcrossReplicas flights;

    @InjectMocks
    private DeleteAircraftUseCase deleteAircraftUseCase;

    private RegistrationNumber registrationNumber;
    private Aircraft aircraft;

    @BeforeEach
    void setUp() {
        registrationNumber = new RegistrationNumber("CS-TPA");
        AircraftModel model = new AircraftModel("A320", Manufacturer.AIRBUS, 26730.0, 6150.0, 833.0, null, 180);
        aircraft = new Aircraft(AircraftStatus.AVAILABLE, LocalDate.of(2020, 1, 1), model, registrationNumber, 150, 5000.0, List.of());
    }

    private static FlightCopy flightOf(String registration) {
        OffsetDateTime departure = OffsetDateTime.of(2025, 1, 1, 8, 0, 0, 0, ZoneOffset.UTC);
        return new FlightCopy("F1", departure, departure.plusHours(2), FlightCopyStatus.SCHEDULED, "OPO", "LIS",
                new RegistrationNumber(registration), 1);
    }

    private static Versioned<MaintenanceRecord> recordOf(String registration) {
        MaintenancePart part = new MaintenancePart("P001", "Part1", "Desc1", 10, 2, MaintenanceComponent.ENGINE);
        MaintenanceTemplate template = new MaintenanceTemplate("T1", MaintenanceType.INSPECTION, List.of(new ModelName("A320")), List.of("Check1"), 100, 30);
        return new Versioned<>(new MaintenanceRecord(UUID.randomUUID(), "Check", LocalDateTime.of(2025, 1, 1, 8, 0), 4,
                List.of(part), "", template, MaintenanceStatus.PLANNED, Set.of(MaintenanceComponent.ENGINE),
                new RegistrationNumber(registration), BigDecimal.TEN, null), 0L);
    }

    @Test
    void ensureAircraftIsDeletedSuccessfully() {
        when(aircraftRepository.findByRegistrationNumber(registrationNumber)).thenReturn(Optional.of(aircraft));
        when(flights.findAll()).thenReturn(List.of(flightOf("CS-TPB")));
        when(records.findAll()).thenReturn(List.of(recordOf("CS-TPB")));

        assertDoesNotThrow(() -> deleteAircraftUseCase.execute(registrationNumber));
        verify(aircraftRepository, times(1)).delete(aircraft);
    }

    @Test
    void ensureExceptionWhenAircraftNotFound() {
        when(aircraftRepository.findByRegistrationNumber(registrationNumber)).thenReturn(Optional.empty());

        assertThrows(AircraftNotFoundException.class, () -> deleteAircraftUseCase.execute(registrationNumber));
        verify(aircraftRepository, never()).delete(any());
    }

    @Test
    void ensureExceptionWhenAircraftInUseByFlights() {
        when(aircraftRepository.findByRegistrationNumber(registrationNumber)).thenReturn(Optional.of(aircraft));
        when(flights.findAll()).thenReturn(List.of(flightOf("CS-TPA")));

        assertThrows(ResourceInUseException.class, () -> deleteAircraftUseCase.execute(registrationNumber));
        verify(aircraftRepository, never()).delete(any());
    }

    @Test
    void ensureExceptionWhenAircraftInUseByMaintenance() {
        when(aircraftRepository.findByRegistrationNumber(registrationNumber)).thenReturn(Optional.of(aircraft));
        when(flights.findAll()).thenReturn(List.of());
        when(records.findAll()).thenReturn(List.of(recordOf("CS-TPA")));

        assertThrows(ResourceInUseException.class, () -> deleteAircraftUseCase.execute(registrationNumber));
        verify(aircraftRepository, never()).delete(any());
    }
}
