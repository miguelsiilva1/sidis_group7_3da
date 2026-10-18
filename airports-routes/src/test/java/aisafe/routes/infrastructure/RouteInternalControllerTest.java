package aisafe.routes.infrastructure;

import aisafe.routes.application.ViewRouteForServicesUseCase;
import aisafe.routes.application.dtos.RouteForServicesResponse;
import aisafe.routes.domain.RouteNotFoundException;
import aisafe.routes.domain.RouteStatus;
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

@WebMvcTest(RouteInternalController.class)
@AutoConfigureMockMvc(addFilters = false)
class RouteInternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ViewRouteForServicesUseCase viewRouteForServices;

    @MockitoBean
    private JwtTokenValidator tokenValidator;

    @MockitoBean
    private PeerClient peerClient;

    @Test
    void ensureExistingRouteIsFound() throws Exception {
        when(viewRouteForServices.execute("LIS", "OPO"))
                .thenReturn(new RouteForServicesResponse("LIS", "OPO", RouteStatus.ACTIVE, 45, 100, 280.0, 274.25));

        mockMvc.perform(get("/internal/routes/LIS/OPO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.minimumCapacity").value(100))
                .andExpect(jsonPath("$.distanceKm").value(274.25));
    }

    @Test
    void ensureUnknownRouteReturns404WhenThereAreNoPeers() throws Exception {
        when(viewRouteForServices.execute("LIS", "XXX")).thenThrow(new RouteNotFoundException("LIS-XXX"));

        mockMvc.perform(get("/internal/routes/LIS/XXX"))
                .andExpect(status().isNotFound());
    }
}
