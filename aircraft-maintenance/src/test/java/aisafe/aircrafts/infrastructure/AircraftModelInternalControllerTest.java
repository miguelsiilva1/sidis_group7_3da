package aisafe.aircrafts.infrastructure;

import aisafe.aircrafts.application.ViewAircraftModelForServicesUseCase;
import aisafe.aircrafts.application.dtos.AircraftModelForServicesResponse;
import aisafe.aircrafts.domain.AircraftModelNotFoundException;
import aisafe.aircrafts.domain.Manufacturer;
import aisafe.shared.infrastructure.peers.PeerClient;
import aisafe.shared.infrastructure.security.JwtTokenValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AircraftModelInternalController.class)
@AutoConfigureMockMvc(addFilters = false)
class AircraftModelInternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ViewAircraftModelForServicesUseCase viewAircraftModelForServices;

    @MockitoBean
    private JwtTokenValidator tokenValidator;

    @MockitoBean
    private PeerClient peerClient;

    @Test
    void ensureExistingModelIsFound() throws Exception {
        when(viewAircraftModelForServices.execute("Airbus A320neo"))
                .thenReturn(new AircraftModelForServicesResponse("Airbus A320neo", Manufacturer.AIRBUS));

        mockMvc.perform(get("/internal/aircraft-models/Airbus A320neo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelName").value("Airbus A320neo"))
                .andExpect(jsonPath("$.manufacturer").value("AIRBUS"));
    }

    @Test
    void ensureUnknownModelReturns404WhenThereAreNoPeers() throws Exception {
        when(viewAircraftModelForServices.execute("Unknown")).thenThrow(new AircraftModelNotFoundException("Aircraft model not found"));

        mockMvc.perform(get("/internal/aircraft-models/Unknown"))
                .andExpect(status().isNotFound());
    }
}
