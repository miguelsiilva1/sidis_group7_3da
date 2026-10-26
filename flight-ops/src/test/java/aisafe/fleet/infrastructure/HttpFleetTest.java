package aisafe.fleet.infrastructure;

import aisafe.fleet.domain.AircraftConflictException;
import aisafe.fleet.domain.AircraftInfo;
import aisafe.fleet.domain.AircraftNotFoundException;
import aisafe.fleet.domain.FleetUnavailableException;
import aisafe.fleet.domain.FlightCopy;
import aisafe.fleet.domain.RouteConflictException;
import aisafe.fleet.domain.RouteInfo;
import aisafe.fleet.domain.RouteNotFoundException;
import aisafe.shared.infrastructure.security.ServiceTokens;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Uses a real HTTP server on localhost as external services.
 */
class HttpFleetTest {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    private HttpServer server;
    private final ConcurrentLinkedQueue<String> paths = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> authorizationHeaders = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> methods = new ConcurrentLinkedQueue<>();

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
            methods.add(exchange.getRequestMethod());
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
    void ensureRouteIsReadWithToken() throws IOException {
        HttpFleet fleet = fleetAnswering(200, """
                {"originIataCode":"LIS","destinationIataCode":"OPO","status":"ACTIVE",
                 "estimatedFlightTime":50,"minimumCapacity":100,"minimumRange":300.0,"distanceKm":314.5}
                """);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer user-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Optional<RouteInfo> route = fleet.findRoute("LIS", "OPO");

        assertTrue(route.isPresent());
        assertEquals("ACTIVE", route.get().status());
        assertEquals(100, route.get().minimumCapacity());
        assertEquals(314.5, route.get().distanceKm());
        assertEquals("/internal/routes/LIS/OPO", paths.peek());
        assertEquals("Bearer user-token", authorizationHeaders.peek());
    }

    @Test
    void ensureUnknownRouteIsEmpty() throws IOException {
        HttpFleet fleet = fleetAnswering(404, "{\"message\":\"not found\"}");

        assertTrue(fleet.findRoute("LIS", "OPO").isEmpty());
    }

    @Test
    void ensureSendFlightToAircraftSuccess() throws IOException {
        HttpFleet fleet = fleetAnswering(200, "{\"id\":\"1\"}");
        FlightCopy copy = new FlightCopy("1", OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.now(ZoneOffset.UTC).plusHours(2),
                "SCHEDULED", "LIS", "OPO", "CS-TKA", 1L);

        assertDoesNotThrow(() -> fleet.sendFlightToAircraft(copy));
        assertEquals("/internal/flights/1", paths.peek());
        assertEquals("PUT", methods.peek());
    }

    @Test
    void ensureCopyOutsideAUsersRequestCarriesTheServiceToken() throws IOException {
        fleetAnswering(200, "{}");
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        // Sample data at startup and retried compensations run without a user's request
        HttpFleet fleet = new HttpFleet(url, url, 500, 2000, new ServiceTokens(SECRET, "flight-ops-0"));
        FlightCopy copy = new FlightCopy("1", OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.now(ZoneOffset.UTC).plusHours(2),
                "SCHEDULED", "LIS", "OPO", "CS-TKA", 1L);

        fleet.sendFlightToAircraft(copy);

        assertTrue(authorizationHeaders.peek().startsWith("Bearer "), authorizationHeaders.peek());
    }

    @Test
    void ensureSendFlightToAircraftNotFoundThrowsException() throws IOException {
        HttpFleet fleet = fleetAnswering(404, "{\"message\":\"not found\"}");
        FlightCopy copy = new FlightCopy("1", OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.now(ZoneOffset.UTC).plusHours(2),
                "SCHEDULED", "LIS", "OPO", "CS-TKA", 1L);

        assertThrows(AircraftNotFoundException.class, () -> fleet.sendFlightToAircraft(copy));
    }

    @Test
    void ensureSendFlightToAircraftConflictThrowsException() throws IOException {
        HttpFleet fleet = fleetAnswering(409, "{\"message\":\"conflict\"}");
        FlightCopy copy = new FlightCopy("1", OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.now(ZoneOffset.UTC).plusHours(2),
                "SCHEDULED", "LIS", "OPO", "CS-TKA", 1L);

        assertThrows(AircraftConflictException.class, () -> fleet.sendFlightToAircraft(copy));
    }

    @Test
    void ensureSendFlightToRoutesSuccess() throws IOException {
        HttpFleet fleet = fleetAnswering(200, "{\"id\":\"1\"}");
        FlightCopy copy = new FlightCopy("1", OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.now(ZoneOffset.UTC).plusHours(2),
                "SCHEDULED", "LIS", "OPO", "CS-TKA", 1L);

        assertDoesNotThrow(() -> fleet.sendFlightToRoutes(copy));
        assertEquals("/internal/flights/1", paths.peek());
        assertEquals("PUT", methods.peek());
    }

    @Test
    void ensureSendFlightToRoutesNotFoundThrowsException() throws IOException {
        HttpFleet fleet = fleetAnswering(404, "{\"message\":\"not found\"}");
        FlightCopy copy = new FlightCopy("1", OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.now(ZoneOffset.UTC).plusHours(2),
                "SCHEDULED", "LIS", "OPO", "CS-TKA", 1L);

        assertThrows(RouteNotFoundException.class, () -> fleet.sendFlightToRoutes(copy));
    }

    @Test
    void ensureSendFlightToRoutesConflictThrowsException() throws IOException {
        HttpFleet fleet = fleetAnswering(409, "{\"message\":\"conflict\"}");
        FlightCopy copy = new FlightCopy("1", OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.now(ZoneOffset.UTC).plusHours(2),
                "SCHEDULED", "LIS", "OPO", "CS-TKA", 1L);

        assertThrows(RouteConflictException.class, () -> fleet.sendFlightToRoutes(copy));
    }

    @Test
    void ensureFailureIsUnavailableNotMissing() throws IOException {
        HttpFleet fleet = fleetAnswering(503, "");

        assertThrows(FleetUnavailableException.class, () -> fleet.findAircraft("CS-TKA"));
        assertThrows(FleetUnavailableException.class, () -> fleet.findRoute("LIS", "OPO"));
    }

    @Test
    void ensureNoAnswerIsUnavailableNotMissing() throws IOException {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }
        HttpFleet fleet = new HttpFleet("http://127.0.0.1:" + freePort, 500, 1000);

        assertThrows(FleetUnavailableException.class, () -> fleet.findAircraft("CS-TKA"));
    }
}
