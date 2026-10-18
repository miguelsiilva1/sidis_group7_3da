package aisafe.aircrafts.infrastructure;

import aisafe.aircrafts.application.ViewAircraftForServicesUseCase;
import aisafe.aircrafts.application.dtos.AircraftForServicesResponse;
import aisafe.aircrafts.domain.AircraftNotFoundException;
import aisafe.aircrafts.domain.AircraftStatus;
import aisafe.shared.infrastructure.peers.PeerClient;
import aisafe.shared.infrastructure.security.JwtTokenValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AircraftInternalController.class)
@AutoConfigureMockMvc(addFilters = false)
class AircraftInternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ViewAircraftForServicesUseCase viewAircraftForServices;

    @MockitoBean
    private JwtTokenValidator tokenValidator;

    @MockitoBean
    private PeerClient peerClient;

    @Test
    void ensureGetAircraftReturnsDataNeededByOtherServices() throws Exception {
        when(viewAircraftForServices.execute(any())).thenReturn(
                new AircraftForServicesResponse("CS-TKA", "A320", AircraftStatus.AVAILABLE, 180, 6000.0, 4.2));

        mockMvc.perform(get("/internal/aircrafts/CS-TKA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.fuelConsumptionPerDistanceUnit").value(4.2));
    }

    @Test
    void ensureUnknownAircraftReturns404WhenThereAreNoPeers() throws Exception {
        when(viewAircraftForServices.execute(any())).thenThrow(new AircraftNotFoundException("Aircraft not found"));

        mockMvc.perform(get("/internal/aircrafts/CS-XXX"))
                .andExpect(status().isNotFound());
    }
}
