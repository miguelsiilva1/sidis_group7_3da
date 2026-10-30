package aisafe.maintenance.infrastructure;

import aisafe.aircraftmaintenance.infrastructure.SecurityConfig;
import aisafe.maintenance.application.*;
import aisafe.shared.application.dtos.BulkImportResult;
import aisafe.shared.infrastructure.peers.PeerClient;
import aisafe.shared.infrastructure.security.JwtAuthenticationFilter;
import aisafe.shared.infrastructure.security.JwtTokenValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The roles allowed to import maintenance parts: the same as for creating one (US226).
 */
@WebMvcTest(MaintenanceController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@AutoConfigureMockMvc
class MaintenanceImportSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateMaintenanceTemplateUseCase createMaintenanceTemplateUseCase;
    @MockitoBean
    private CreateMaintenanceRecordUseCase createMaintenanceRecordUseCase;
    @MockitoBean
    private CreateMaintenancePartUseCase createMaintenancePartUseCase;
    @MockitoBean
    private UpdateMaintenanceRecordUseCase updateMaintenanceRecordUseCase;
    @MockitoBean
    private ViewAllMaintenanceRecordsUseCase viewAllMaintenanceRecordsUseCase;
    @MockitoBean
    private ViewTotalMaintenanceHoursInFleetUseCase viewTotalMaintenanceHoursInFleetUseCase;
    @MockitoBean
    private DeleteMaintenanceRecordUseCase deleteMaintenanceRecordUseCase;
    @MockitoBean
    private DeleteMaintenanceTemplateUseCase deleteMaintenanceTemplateUseCase;
    @MockitoBean
    private DeleteMaintenancePartUseCase deleteMaintenancePartUseCase;
    @MockitoBean
    private UpdateMaintenancePartUseCase updateMaintenancePartUseCase;
    @MockitoBean
    private UpdateMaintenanceTemplateUseCase updateMaintenanceTemplateUseCase;
    @MockitoBean
    private SearchMaintenancePartUseCase searchMaintenancePartUseCase;
    @MockitoBean
    private SearchMaintenanceRecordsUseCase searchMaintenanceRecordsUseCase;
    @MockitoBean
    private ViewOngoingMaintenanceUseCase viewOngoingMaintenanceUseCase;
    @MockitoBean
    private ViewMaintenanceCostByAircraftUseCase viewMaintenanceCostByAircraftUseCase;
    @MockitoBean
    private ViewMaintenanceCostByModelUseCase viewMaintenanceCostByModelUseCase;
    @MockitoBean
    private ViewAverageMaintenanceTurnaroundUseCase viewAverageTurnaroundUseCase;
    @MockitoBean
    private ViewMaintenanceDueAircraftUseCase viewMaintenanceDueAircraftUseCase;
    @MockitoBean
    private ImportMaintenanceTemplatesUseCase importMaintenanceTemplatesUseCase;
    @MockitoBean
    private ImportMaintenanceRecordsUseCase importMaintenanceRecordsUseCase;
    @MockitoBean
    private ImportMaintenancePartsUseCase importMaintenancePartsUseCase;
    @MockitoBean
    private JwtTokenValidator tokenValidator;
    @MockitoBean
    private PeerClient peerClient;

    private final MockMultipartFile file = new MockMultipartFile(
            "file", "parts.csv", MediaType.TEXT_PLAIN_VALUE, "header\ndata".getBytes());

    @Test
    @WithMockUser(roles = "MAINTENANCE_SUPERVISOR")
    void ensureSupervisorCanImportParts() throws Exception {
        BulkImportResult<String> result = new BulkImportResult<>();
        result.addSuccess("P001");
        when(importMaintenancePartsUseCase.execute(any())).thenReturn(result);

        mockMvc.perform(multipart("/api/maintenance/parts/import").file(file))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "MAINTENANCE_TECHNICIAN")
    void ensureTechnicianCanImportParts() throws Exception {
        BulkImportResult<String> result = new BulkImportResult<>();
        result.addSuccess("P001");
        when(importMaintenancePartsUseCase.execute(any())).thenReturn(result);

        mockMvc.perform(multipart("/api/maintenance/parts/import").file(file))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "ATCC")
    void ensureOtherRolesCannotImportParts() throws Exception {
        mockMvc.perform(multipart("/api/maintenance/parts/import").file(file))
                .andExpect(status().isForbidden());
    }
}
