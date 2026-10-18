package aisafe.aircrafts.application;

import aisafe.aircrafts.application.dtos.ListAircraftRequest;
import aisafe.aircrafts.application.dtos.AircraftResponse;
import aisafe.aircrafts.domain.*;
import aisafe.shared.domain.PaginatedResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListAircraftUseCaseTest {

    @Mock
    private AircraftAcrossReplicas aircraftAcrossReplicas;

    @InjectMocks
    private ListAircraftUseCase listAircraftUseCase;

    private Aircraft aircraft;

    @BeforeEach
    void setUp() {
        aircraft = aircraft("CS-TPA", LocalDate.of(2020, 1, 1));
    }

    private static Aircraft aircraft(String registration, LocalDate manufacturingDate) {
        AircraftModel model = new AircraftModel("A320", Manufacturer.AIRBUS, 26730.0, 6150.0, 833.0, null, 180);
        return new Aircraft(AircraftStatus.AVAILABLE, manufacturingDate, model, new RegistrationNumber(registration), 150, 5000.0, List.of());
    }

    @Test
    void ensureListReturnsResultsSuccessfully() {
        when(aircraftAcrossReplicas.findAll()).thenReturn(List.of(aircraft));

        PaginatedResult<AircraftResponse> result = listAircraftUseCase.execute(new ListAircraftRequest(0, 10));

        assertNotNull(result);
        assertEquals(1, result.data().size());
        AircraftResponse response = result.data().get(0);
        assertEquals("CS-TPA", response.registrationNumber());
        assertEquals("A320", response.model());
        assertEquals(Manufacturer.AIRBUS, response.manufacturer());
        assertEquals(LocalDate.of(2020, 1, 1), response.manufacturingDate());
        assertEquals(AircraftStatus.AVAILABLE, response.status());
        assertEquals(150, response.seatCapacity());
        assertEquals(5000.0, response.range());
        assertTrue(response.features().isEmpty());
    }

    @Test
    void ensureListReturnsEmptyPageWhenNoAircraftExist() {
        when(aircraftAcrossReplicas.findAll()).thenReturn(List.of());

        PaginatedResult<AircraftResponse> result = listAircraftUseCase.execute(new ListAircraftRequest(0, 10));

        assertNotNull(result);
        assertTrue(result.data().isEmpty());
        assertEquals(0L, result.totalElements());
    }

    @Test
    void ensurePaginationIsAppliedAfterMergingEveryReplica() {
        LocalDate date = LocalDate.of(2021, 1, 1);
        when(aircraftAcrossReplicas.findAll()).thenReturn(List.of(
                aircraft("CS-TAA", date), aircraft("CS-TBB", date), aircraft, aircraft("CS-TZZ", date)));

        PaginatedResult<AircraftResponse> page1 = listAircraftUseCase.execute(new ListAircraftRequest(1, 2));
        PaginatedResult<AircraftResponse> page5 = listAircraftUseCase.execute(new ListAircraftRequest(5, 2));

        assertEquals(4L, page1.totalElements());
        assertEquals(List.of("CS-TPA", "CS-TZZ"), page1.data().stream().map(AircraftResponse::registrationNumber).toList());
        assertTrue(page5.data().isEmpty());
        assertEquals(4L, page5.totalElements());
    }
}
