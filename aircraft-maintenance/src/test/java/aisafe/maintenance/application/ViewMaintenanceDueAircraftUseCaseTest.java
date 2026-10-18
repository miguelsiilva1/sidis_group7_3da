package aisafe.maintenance.application;

import aisafe.aircrafts.application.AircraftAcrossReplicas;
import aisafe.flightcopies.application.FlightCopiesAcrossReplicas;
import aisafe.flightcopies.domain.FlightCopy;
import aisafe.flightcopies.domain.FlightCopyStatus;
import aisafe.shared.application.Versioned;
import aisafe.aircrafts.domain.*;
import aisafe.maintenance.application.dtos.MaintenanceDueAircraftResponse;
import aisafe.maintenance.domain.*;
import aisafe.shared.domain.PaginatedResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ViewMaintenanceDueAircraftUseCaseTest {

    @Mock
    private AircraftAcrossReplicas aircraftAcrossReplicas;

    @Mock
    private MaintenanceRecordsAcrossReplicas records;

    @Mock
    private MaintenanceTemplateRepository maintenanceTemplateRepository;

    @Mock
    private FlightCopiesAcrossReplicas flights;

    private ViewMaintenanceDueAircraftUseCase useCase;

    private Aircraft aircraft;
    private AircraftModel model;
    private MaintenanceTemplate template;

    @BeforeEach
    void setUp() {
        useCase = new ViewMaintenanceDueAircraftUseCase(
                aircraftAcrossReplicas,
                records,
                maintenanceTemplateRepository,
                flights,
                300.0,
                365L
        );

        model = new AircraftModel("B737", Manufacturer.BOEING, 20000.0, 5000.0, 800.0, null, 180);
        aircraft = new Aircraft(AircraftStatus.AVAILABLE, LocalDate.now().minusDays(40), model, new RegistrationNumber("CS-TKA"), 180, 5000.0, List.of("WiFi"));
        template = new MaintenanceTemplate("Annual Check", MaintenanceType.INSPECTION, List.of(new ModelName("B737")), List.of("Check"), 100, 30);
    }

    /** A completed flight of CS-TKA that departed yesterday, after every start date used here. */
    private static FlightCopy completedFlightYesterday(double hours) {
        OffsetDateTime departure = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
        return new FlightCopy("F1", departure, departure.plusMinutes((long) (hours * 60)), FlightCopyStatus.COMPLETED,
                "OPO", "LIS", new RegistrationNumber("CS-TKA"), 1);
    }

    @Test
    void ensureAircraftNotDueReturnsEmptyList() {
        // Set manufacturing date to 10 days ago (below 30 days) and flight hours to 20.0 (below 100 hrs)
        Aircraft recentAircraft = new Aircraft(AircraftStatus.AVAILABLE, LocalDate.now().minusDays(10), model, new RegistrationNumber("CS-TKA"), 180, 5000.0, List.of("WiFi"));

        when(aircraftAcrossReplicas.findAll()).thenReturn(List.of(recentAircraft));
        when(maintenanceTemplateRepository.findAll()).thenReturn(List.of(template));
        when(records.findAll()).thenReturn(List.of());
        when(flights.findAll()).thenReturn(List.of(completedFlightYesterday(20.0)));

        PaginatedResult<MaintenanceDueAircraftResponse> result = useCase.execute(0, 20);
        assertTrue(result.data().isEmpty());
    }

    @Test
    void ensureAircraftDueForFlightHoursIsReturned() {
        when(aircraftAcrossReplicas.findAll()).thenReturn(List.of(aircraft));
        when(maintenanceTemplateRepository.findAll()).thenReturn(List.of(template));
        when(records.findAll()).thenReturn(List.of());
        // Flight hours exceeds limit (120 >= 100)
        when(flights.findAll()).thenReturn(List.of(completedFlightYesterday(120.0)));

        PaginatedResult<MaintenanceDueAircraftResponse> result = useCase.execute(0, 20);
        assertEquals(1, result.data().size());
        assertEquals("CS-TKA", result.data().get(0).registrationNumber());
        assertTrue(result.data().get(0).dueReason().contains("Exceeded flight hours limit"));
    }

    @Test
    void ensureAircraftDueForCalendarDaysIsReturned() {
        // Manufacturing date is 40 days ago (exceeds template limit of 30 days)
        when(aircraftAcrossReplicas.findAll()).thenReturn(List.of(aircraft));
        when(maintenanceTemplateRepository.findAll()).thenReturn(List.of(template));
        when(records.findAll()).thenReturn(List.of());
        // Flight hours is below limit (10.0 < 100)
        when(flights.findAll()).thenReturn(List.of(completedFlightYesterday(10.0)));

        PaginatedResult<MaintenanceDueAircraftResponse> result = useCase.execute(0, 20);
        assertEquals(1, result.data().size());
        assertEquals("CS-TKA", result.data().get(0).registrationNumber());
        assertTrue(result.data().get(0).dueReason().contains("Exceeded elapsed days limit"));
    }

    @Test
    void ensureCompletedMaintenanceResetsDueState() {
        // Last completed record was 5 days ago (less than 30)
        MaintenancePart part = new MaintenancePart("P123", "Part 1", "Desc", 10, 5, MaintenanceComponent.ENGINE);
        MaintenanceRecord record = new MaintenanceRecord(
                UUID.randomUUID(), "R1", LocalDateTime.now().minusDays(5), 4,
                List.of(part), "Notes", template, MaintenanceStatus.COMPLETED,
                Set.of(MaintenanceComponent.ENGINE), new RegistrationNumber("CS-TKA"), BigDecimal.valueOf(100), LocalDateTime.now().minusDays(5)
        );

        when(aircraftAcrossReplicas.findAll()).thenReturn(List.of(aircraft));
        when(maintenanceTemplateRepository.findAll()).thenReturn(List.of(template));
        when(records.findAll()).thenReturn(List.of(new Versioned<>(record, 0L)));
        // Flight hours since last completed was 10.0 (less than 100)
        when(flights.findAll()).thenReturn(List.of(completedFlightYesterday(10.0)));

        PaginatedResult<MaintenanceDueAircraftResponse> result = useCase.execute(0, 20);
        assertTrue(result.data().isEmpty());
    }

    @Test
    void ensureFallbackToGlobalThresholdsWhenNoTemplatesApply() {
        // Template is for A320, so does not apply to our B737 aircraft
        MaintenanceTemplate diffTemplate = new MaintenanceTemplate("Annual Check", MaintenanceType.INSPECTION, List.of(new ModelName("A320")), List.of("Check"), 100, 30);

        when(aircraftAcrossReplicas.findAll()).thenReturn(List.of(aircraft));
        when(maintenanceTemplateRepository.findAll()).thenReturn(List.of(diffTemplate));
        when(records.findAll()).thenReturn(List.of());
        // Global threshold is 300 hrs, we return 350.0 hrs
        when(flights.findAll()).thenReturn(List.of(completedFlightYesterday(350.0)));

        PaginatedResult<MaintenanceDueAircraftResponse> result = useCase.execute(0, 20);
        assertEquals(1, result.data().size());
        assertEquals("CS-TKA", result.data().get(0).registrationNumber());
        assertTrue(result.data().get(0).dueReason().contains("Exceeded default flight hours limit"));
    }

    @Test
    void ensureFallbackToGlobalThresholdsWithCompletedRecordAndDaysLimit() {
        MaintenanceTemplate diffTemplate = new MaintenanceTemplate("Annual Check", MaintenanceType.INSPECTION, List.of(new ModelName("A320")), List.of("Check"), 100, 30);
        MaintenancePart part = new MaintenancePart("P123", "Part 1", "Desc", 10, 5, MaintenanceComponent.ENGINE);
        MaintenanceRecord record = new MaintenanceRecord(
                UUID.randomUUID(), "R1", LocalDateTime.now().minusDays(400), 4,
                List.of(part), "Notes", diffTemplate, MaintenanceStatus.COMPLETED,
                Set.of(MaintenanceComponent.ENGINE), new RegistrationNumber("CS-TKA"), BigDecimal.valueOf(100), LocalDateTime.now().minusDays(400)
        );

        when(aircraftAcrossReplicas.findAll()).thenReturn(List.of(aircraft));
        when(maintenanceTemplateRepository.findAll()).thenReturn(List.of(diffTemplate));
        when(records.findAll()).thenReturn(List.of(new Versioned<>(record, 0L)));
        when(flights.findAll()).thenReturn(List.of(completedFlightYesterday(10.0)));

        PaginatedResult<MaintenanceDueAircraftResponse> result = useCase.execute(0, 20);
        assertEquals(1, result.data().size());
        assertTrue(result.data().get(0).dueReason().contains("Exceeded default elapsed days limit"));
    }

    @Test
    void ensureFallbackToGlobalThresholdsWithNullFlightHours() {
        MaintenanceTemplate diffTemplate = new MaintenanceTemplate("Annual Check", MaintenanceType.INSPECTION, List.of(new ModelName("A320")), List.of("Check"), 100, 30);
        Aircraft recentAircraft = new Aircraft(AircraftStatus.AVAILABLE, LocalDate.now().minusDays(10), model, new RegistrationNumber("CS-TKA"), 180, 5000.0, List.of("WiFi"));

        when(aircraftAcrossReplicas.findAll()).thenReturn(List.of(recentAircraft));
        when(maintenanceTemplateRepository.findAll()).thenReturn(List.of(diffTemplate));
        when(records.findAll()).thenReturn(List.of());
        when(flights.findAll()).thenReturn(List.of());

        PaginatedResult<MaintenanceDueAircraftResponse> result = useCase.execute(0, 20);
        assertTrue(result.data().isEmpty());
    }

    @Test
    void ensureExecuteWithPageOutOfBoundsReturnsEmpty() {
        when(aircraftAcrossReplicas.findAll()).thenReturn(List.of(aircraft));
        when(maintenanceTemplateRepository.findAll()).thenReturn(List.of(template));
        when(records.findAll()).thenReturn(List.of());
        when(flights.findAll()).thenReturn(List.of(completedFlightYesterday(120.0)));

        PaginatedResult<MaintenanceDueAircraftResponse> result = useCase.execute(5, 1);
        assertTrue(result.data().isEmpty());
        assertEquals(1, result.totalElements());
    }

    @Test
    void ensureAircraftDueWhenLastCompletedRecordHasNullCompletedAt() {
        MaintenancePart part = new MaintenancePart("P123", "Part 1", "Desc", 10, 5, MaintenanceComponent.ENGINE);
        MaintenanceRecord record = new MaintenanceRecord(
                UUID.randomUUID(), "R1", LocalDateTime.now().minusDays(5), 4,
                List.of(part), "Notes", template, MaintenanceStatus.COMPLETED,
                Set.of(MaintenanceComponent.ENGINE), new RegistrationNumber("CS-TKA"), BigDecimal.valueOf(100), null // null completedAt
        );

        when(aircraftAcrossReplicas.findAll()).thenReturn(List.of(aircraft));
        when(maintenanceTemplateRepository.findAll()).thenReturn(List.of(template));
        when(records.findAll()).thenReturn(List.of(new Versioned<>(record, 0L)));
        when(flights.findAll()).thenReturn(List.of(completedFlightYesterday(10.0)));

        PaginatedResult<MaintenanceDueAircraftResponse> result = useCase.execute(0, 20);
        // It falls back to manufacturing date, which is 40 days ago, making it due (> 30 days)
        assertEquals(1, result.data().size());
        assertTrue(result.data().get(0).dueReason().contains("Exceeded elapsed days limit"));
    }

    @Test
    void ensureFallbackToGlobalThresholdsWhenLastCompletedRecordHasNullCompletedAt() {
        MaintenanceTemplate diffTemplate = new MaintenanceTemplate("Annual Check", MaintenanceType.INSPECTION, List.of(new ModelName("A320")), List.of("Check"), 100, 30);
        MaintenancePart part = new MaintenancePart("P123", "Part 1", "Desc", 10, 5, MaintenanceComponent.ENGINE);
        MaintenanceRecord record = new MaintenanceRecord(
                UUID.randomUUID(), "R1", LocalDateTime.now().minusDays(5), 4,
                List.of(part), "Notes", diffTemplate, MaintenanceStatus.COMPLETED,
                Set.of(MaintenanceComponent.ENGINE), new RegistrationNumber("CS-TKA"), BigDecimal.valueOf(100), null // null completedAt
        );

        when(aircraftAcrossReplicas.findAll()).thenReturn(List.of(aircraft));
        when(maintenanceTemplateRepository.findAll()).thenReturn(List.of(diffTemplate));
        when(records.findAll()).thenReturn(List.of(new Versioned<>(record, 0L)));
        when(flights.findAll()).thenReturn(List.of(completedFlightYesterday(10.0)));

        PaginatedResult<MaintenanceDueAircraftResponse> result = useCase.execute(0, 20);
        // Global days threshold is 365 days. Manufacturing date is 40 days ago, so not due on days.
        // Flight hours is 10.0 (< 300), so not due on flight hours.
        assertTrue(result.data().isEmpty());
    }

    @Test
    void ensureAircraftNotDueWhenFlightHoursIsNullAndNoDaysLimit() {
        // Set manufacturing date to 10 days ago (below 30 days)
        Aircraft recentAircraft = new Aircraft(AircraftStatus.AVAILABLE, LocalDate.now().minusDays(10), model, new RegistrationNumber("CS-TKA"), 180, 5000.0, List.of("WiFi"));

        when(aircraftAcrossReplicas.findAll()).thenReturn(List.of(recentAircraft));
        when(maintenanceTemplateRepository.findAll()).thenReturn(List.of(template));
        when(records.findAll()).thenReturn(List.of());
        when(flights.findAll()).thenReturn(List.of());

        PaginatedResult<MaintenanceDueAircraftResponse> result = useCase.execute(0, 20);
        assertTrue(result.data().isEmpty());
    }

    @Test
    void ensureRecordsAndFlightsOfOtherAircraftOrNotCompletedAreIgnored() {
        Aircraft recentAircraft = new Aircraft(AircraftStatus.AVAILABLE, LocalDate.now().minusDays(10), model, new RegistrationNumber("CS-TKA"), 180, 5000.0, List.of("WiFi"));
        OffsetDateTime departure = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
        when(aircraftAcrossReplicas.findAll()).thenReturn(List.of(recentAircraft));
        when(maintenanceTemplateRepository.findAll()).thenReturn(List.of(template));
        when(records.findAll()).thenReturn(List.of());
        when(flights.findAll()).thenReturn(List.of(
                new FlightCopy("F1", departure, departure.plusHours(200), FlightCopyStatus.COMPLETED, "OPO", "LIS", new RegistrationNumber("CS-TKB"), 1),
                new FlightCopy("F2", departure, departure.plusHours(200), FlightCopyStatus.SCHEDULED, "OPO", "LIS", new RegistrationNumber("CS-TKA"), 1)));

        PaginatedResult<MaintenanceDueAircraftResponse> result = useCase.execute(0, 20);
        assertTrue(result.data().isEmpty());
    }

    @Test
    void ensureMostRecentlyCompletedRecordOfTheTemplateCountsWhateverReplicaHoldsIt() {
        MaintenancePart part = new MaintenancePart("P123", "Part 1", "Desc", 10, 5, MaintenanceComponent.ENGINE);
        MaintenanceRecord old = new MaintenanceRecord(UUID.randomUUID(), "R1", LocalDateTime.now().minusDays(60), 4,
                List.of(part), "Notes", template, MaintenanceStatus.COMPLETED, Set.of(MaintenanceComponent.ENGINE),
                new RegistrationNumber("CS-TKA"), BigDecimal.valueOf(100), LocalDateTime.now().minusDays(60));
        MaintenanceRecord recent = new MaintenanceRecord(UUID.randomUUID(), "R2", LocalDateTime.now().minusDays(5), 4,
                List.of(part), "Notes", template, MaintenanceStatus.COMPLETED, Set.of(MaintenanceComponent.ENGINE),
                new RegistrationNumber("CS-TKA"), BigDecimal.valueOf(100), LocalDateTime.now().minusDays(5));
        when(aircraftAcrossReplicas.findAll()).thenReturn(List.of(aircraft));
        when(maintenanceTemplateRepository.findAll()).thenReturn(List.of(template));
        when(records.findAll()).thenReturn(List.of(new Versioned<>(old, 0L), new Versioned<>(recent, 0L)));
        when(flights.findAll()).thenReturn(List.of());

        PaginatedResult<MaintenanceDueAircraftResponse> result = useCase.execute(0, 20);
        assertTrue(result.data().isEmpty(), "counted from the record completed 5 days ago, not 60");
    }
}
