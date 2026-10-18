package aisafe.fleet.infrastructure;

import aisafe.fleet.domain.AircraftInfo;
import aisafe.fleet.domain.FleetUnavailableException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Uses a real HTTP server on localhost as aircraft-maintenance.
 */
class HttpFleetTest {

    private HttpServer server;
    private final ConcurrentLinkedQueue<String> paths = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> authorizationHeaders = new ConcurrentLinkedQueue<>();

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        RequestContextHolder.resetRequestAttributes();
    }

    private HttpFleet fleetAnswering(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getRawPath());
            authorizationHeaders.add(String.valueOf(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION)));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
            exchange.close();
        });
        server.start();
        return new HttpFleet("http://127.0.0.1:" + server.getAddress().getPort(), 500, 2000);
    }

    @Test
    void ensureAircraftIsReadWithTheUsersToken() throws IOException {
        HttpFleet fleet = fleetAnswering(200, """
                {"registrationNumber":"CS-TKA","model":"Airbus A320neo","status":"AVAILABLE",
                 "seatCapacity":180,"range":6570.0,"fuelConsumptionPerDistanceUnit":3.68}
                """);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer user-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Optional<AircraftInfo> aircraft = fleet.findAircraft("CS-TKA");

        assertTrue(aircraft.isPresent());
        assertEquals(180, aircraft.get().seatCapacity());
        assertEquals(6570.0, aircraft.get().range());
        assertEquals("/internal/aircrafts/CS-TKA", paths.peek());
        assertEquals("Bearer user-token", authorizationHeaders.peek());
    }

    @Test
    void ensureUnknownAircraftIsEmpty() throws IOException {
        HttpFleet fleet = fleetAnswering(404, "{\"message\":\"not found\"}");

        assertTrue(fleet.findAircraft("CS-XXX").isEmpty());
    }

    @Test
    void ensureModelNameIsEncodedAndExistenceFollowsTheAnswer() throws IOException {
        HttpFleet fleet = fleetAnswering(200, "{\"modelName\":\"Airbus A320neo\",\"manufacturer\":\"AIRBUS\"}");

        assertTrue(fleet.aircraftModelExists("Airbus A320neo"));
        assertEquals("/internal/aircraft-models/Airbus%20A320neo", paths.peek());
    }

    @Test
    void ensureUnknownModelDoesNotExist() throws IOException {
        HttpFleet fleet = fleetAnswering(404, "");

        assertFalse(fleet.aircraftModelExists("Unknown"));
    }

    @Test
    void ensureFailureIsUnavailableNotMissing() throws IOException {
        HttpFleet fleet = fleetAnswering(503, "");

        assertThrows(FleetUnavailableException.class, () -> fleet.findAircraft("CS-TKA"));
    }

    @Test
    void ensureNoAnswerIsUnavailableNotMissing() throws IOException {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }
        HttpFleet fleet = new HttpFleet("http://127.0.0.1:" + freePort, 500, 1000);

        assertThrows(FleetUnavailableException.class, () -> fleet.aircraftModelExists("Airbus A320neo"));
    }
}
