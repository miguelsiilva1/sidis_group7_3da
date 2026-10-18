package aisafe.flightcopies.infrastructure;

import aisafe.aircrafts.domain.AircraftNotFoundException;
import aisafe.flightcopies.application.RecordFlightCopyUseCase;
import aisafe.flightcopies.application.dtos.FlightCopyResponse;
import aisafe.flightcopies.domain.AircraftUnavailableForFlightException;
import aisafe.shared.infrastructure.peers.PeerClient;
import aisafe.shared.infrastructure.security.JwtTokenValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FlightCopyController.class)
@AutoConfigureMockMvc(addFilters = false)
class FlightCopyControllerTest {

    private static final String BODY = """
            {"id":"f-1","departureDateTime":"2026-11-01T10:00:00Z","arrivalDateTime":"2026-11-01T12:00:00Z",
             "status":"SCHEDULED","originIataCode":"LIS","destinationIataCode":"OPO",
             "aircraftRegistration":"CS-TKA","version":1}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecordFlightCopyUseCase recordFlightCopy;

    @MockitoBean
    private JwtTokenValidator tokenValidator;

    @MockitoBean
    private PeerClient peerClient;

    @Test
    void ensurePutStoresCopyAndReturns200() throws Exception {
        OffsetDateTime departure = OffsetDateTime.of(2026, 11, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        when(recordFlightCopy.execute(any())).thenReturn(new FlightCopyResponse("f-1", departure, departure.plusHours(2),
                "SCHEDULED", "LIS", "OPO", "CS-TKA", 1));

        mockMvc.perform(put("/internal/flights/f-1").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("f-1"))
                .andExpect(jsonPath("$.aircraftRegistration").value("CS-TKA"));
    }

    @Test
    void ensureDifferentIdsInPathAndBodyReturn400() throws Exception {
        mockMvc.perform(put("/internal/flights/other").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(recordFlightCopy);
    }

    @Test
    void ensureMissingFieldsReturn400() throws Exception {
        mockMvc.perform(put("/internal/flights/f-1").contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"f-1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ensureUnavailableAircraftReturns409() throws Exception {
        when(recordFlightCopy.execute(any())).thenThrow(new AircraftUnavailableForFlightException("CS-TKA"));

        mockMvc.perform(put("/internal/flights/f-1").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void ensureUnknownAircraftReturns404() throws Exception {
        when(recordFlightCopy.execute(any())).thenThrow(new AircraftNotFoundException("Aircraft not found: CS-TKA"));

        mockMvc.perform(put("/internal/flights/f-1").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
    }
}
