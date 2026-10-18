package aisafe.aircrafts.application;

import aisafe.aircrafts.application.dtos.SearchAircraftUseCaseResponse;
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
class SearchAircraftUseCaseTest {

    @Mock
    private AircraftAcrossReplicas aircraftAcrossReplicas;

    @InjectMocks
    private SearchAircraftUseCase searchAircraftUseCase;

    private static Aircraft aircraft(String registration, String modelName, AircraftStatus status, int year, List<String> features) {
        AircraftModel model = new AircraftModel(modelName, Manufacturer.AIRBUS, 26730.0, 6150.0, 833.0, null, 180);
        return new Aircraft(status, LocalDate.of(year, 1, 1), model, new RegistrationNumber(registration), 150, 5000.0, features);
    }

    private void fleet() {
        // Aircraft of every replica, as the port returns them (ordered by registration)
        when(aircraftAcrossReplicas.findAll()).thenReturn(List.of(
                aircraft("CS-TAA", "A320", AircraftStatus.AVAILABLE, 2020, List.of("WiFi")),
                aircraft("CS-TBB", "A320", AircraftStatus.AVAILABLE, 2018, List.of("WiFi")),
                aircraft("CS-TCC", "A320", AircraftStatus.INACTIVE, 2020, List.of("WiFi")),
                aircraft("CS-TDD", "B737", AircraftStatus.AVAILABLE, 2020, List.of("WiFi")),
                aircraft("CS-TEE", "A320", AircraftStatus.AVAILABLE, 2020, List.of()),
                aircraft("CS-TFF", "A320", AircraftStatus.AVAILABLE, 2020, List.of("WiFi", "USB"))));
    }

    private static List<String> registrations(PaginatedResult<SearchAircraftUseCaseResponse> result) {
        return result.data().stream().map(SearchAircraftUseCaseResponse::registrationNumber).toList();
    }

    @Test
    void ensureSearchAppliesEveryFilter() {
        fleet();

        PaginatedResult<SearchAircraftUseCaseResponse> result = searchAircraftUseCase.execute("A320", "AVAILABLE", 2020, "WiFi", 0, 10);

        assertEquals(List.of("CS-TAA", "CS-TFF"), registrations(result));
        assertEquals(2L, result.totalElements());
    }

    @Test
    void ensureEachFilterIsOptional() {
        fleet();

        assertEquals(6L, searchAircraftUseCase.execute(null, null, null, null, 0, 10).totalElements());
        assertEquals(5L, searchAircraftUseCase.execute("A320", null, null, null, 0, 10).totalElements());
        assertEquals(List.of("CS-TCC"), registrations(searchAircraftUseCase.execute(null, "inactive", null, null, 0, 10)));
        assertEquals(List.of("CS-TBB"), registrations(searchAircraftUseCase.execute(null, null, 2018, null, 0, 10)));
        assertEquals(List.of("CS-TFF"), registrations(searchAircraftUseCase.execute(null, null, null, "USB", 0, 10)));
    }

    @Test
    void ensurePageIsCutAfterTheMatchesOfEveryReplicaAreTogether() {
        fleet();

        PaginatedResult<SearchAircraftUseCaseResponse> page = searchAircraftUseCase.execute("A320", null, null, null, 1, 2);

        assertEquals(List.of("CS-TCC", "CS-TEE"), registrations(page));
        assertEquals(5L, page.totalElements());
    }

    @Test
    void ensureExceptionForInvalidStatus() {
        assertThrows(AircraftInvalidFieldException.class, () -> searchAircraftUseCase.execute(null, "INVALID_STATUS", null, null, 0, 10));
    }

    @Test
    void ensureBlankStatusTreatedAsNull() {
        fleet();

        PaginatedResult<SearchAircraftUseCaseResponse> result = searchAircraftUseCase.execute(null, "   ", null, null, 0, 10);

        assertEquals(6L, result.totalElements());
    }

    @Test
    void ensureSearchAircraftUseCaseResponseHandlesNullManufacturingDate() {
        Aircraft mockAircraft = mock(Aircraft.class);
        when(mockAircraft.getRegistrationNumber()).thenReturn(new RegistrationNumber("CS-TPA"));
        when(mockAircraft.getModel()).thenReturn(new AircraftModel("A320", Manufacturer.AIRBUS, 26730.0, 6150.0, 833.0, null, 180));
        when(mockAircraft.getStatus()).thenReturn(AircraftStatus.AVAILABLE);
        when(mockAircraft.getManufacturingDate()).thenReturn(null);

        SearchAircraftUseCaseResponse response = SearchAircraftUseCaseResponse.from(mockAircraft);

        assertNotNull(response);
        assertEquals("CS-TPA", response.registrationNumber());
        assertEquals("A320", response.model());
        assertEquals("AVAILABLE", response.status());
        assertEquals(0, response.manufacturingYear());
    }
}
