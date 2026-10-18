package aisafe.shared.infrastructure.peers;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Uses real HTTP servers on localhost as peers.
 */
class PeerClientTest {

    private final List<HttpServer> servers = new ArrayList<>();
    private final ConcurrentLinkedQueue<String> forwardedHeaders = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> authorizationHeaders = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> rawPaths = new ConcurrentLinkedQueue<>();

    @AfterEach
    void tearDown() {
        servers.forEach(s -> s.stop(0));
    }

    /** A peer that answers every GET with the given status and body. */
    private String peer(String nodeId, int status, String body) throws IOException {
        return peer(nodeId, new AtomicInteger(), 0, status, body);
    }

    /** A peer that fails with 503 for the first {@code failures} requests, then answers normally. */
    private String peer(String nodeId, AtomicInteger requests, int failures, int status, String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            forwardedHeaders.add(String.valueOf(exchange.getRequestHeaders().getFirst(PeerHeaders.FORWARDED)));
            rawPaths.add(exchange.getRequestURI().getRawPath() + "?" + exchange.getRequestURI().getRawQuery());
            authorizationHeaders.add(String.valueOf(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION)));
            int code = requests.incrementAndGet() <= failures ? 503 : status;
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add(PeerHeaders.NODE_ID, nodeId);
            exchange.sendResponseHeaders(code, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
            exchange.close();
        });
        server.start();
        servers.add(server);
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** An address where nobody is listening. */
    private static String deadPeer() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return "http://127.0.0.1:" + socket.getLocalPort();
        }
    }

    private static PeerClient client(List<String> peers) {
        return new PeerClient("node-0", peers, 300, 500, 2, 10, 3, 60_000, 2_000);
    }

    private static HttpHeaders withToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("token-123");
        return headers;
    }

    @Test
    void ensureFirstPeerThatHasTheResourceAnswers() throws IOException {
        PeerClient client = client(List.of(peer("node-1", 404, ""), peer("node-2", 200, "{\"id\":\"x\"}")));

        PeerClient.PeerLookup lookup = client.getFirst("/api/things/x", withToken());

        assertTrue(lookup.found().isPresent());
        assertEquals("node-2", lookup.found().get().headers().getFirst(PeerHeaders.NODE_ID));
        assertEquals("{\"id\":\"x\"}", new String(lookup.found().get().body(), StandardCharsets.UTF_8));
        assertTrue(lookup.unreachablePeers().isEmpty());
    }

    @Test
    void ensureForwardedRequestsCarryTheForwardingNodeAndTheUserToken() throws IOException {
        PeerClient client = client(List.of(peer("node-1", 404, "")));

        client.getFirst("/api/things/x", withToken());

        assertEquals(List.of("node-0"), List.copyOf(forwardedHeaders));
        assertEquals(List.of("Bearer token-123"), List.copyOf(authorizationHeaders));
    }

    @Test
    void ensureNotFoundAnywhereIsEmptyAndNotPartial() throws IOException {
        PeerClient client = client(List.of(peer("node-1", 404, ""), peer("node-2", 404, "")));

        PeerClient.PeerLookup lookup = client.getFirst("/api/things/x", new HttpHeaders());

        assertTrue(lookup.found().isEmpty());
        assertTrue(lookup.unreachablePeers().isEmpty());
    }

    @Test
    void ensurePeerThatCannotBeReachedIsReportedAsUnreachable() throws IOException {
        String dead = deadPeer();
        PeerClient client = client(List.of(dead, peer("node-2", 404, "")));

        PeerClient.PeerLookup lookup = client.getFirst("/api/things/x", new HttpHeaders());

        assertTrue(lookup.found().isEmpty());
        assertEquals(List.of(dead), lookup.unreachablePeers());
    }

    @Test
    void ensureFailingPeerIsRetriedWithBackoff() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        PeerClient client = client(List.of(peer("node-1", requests, 2, 200, "{}")));

        PeerClient.PeerLookup lookup = client.getFirst("/api/things/x", new HttpHeaders());

        assertTrue(lookup.found().isPresent(), "two 503s and then a 200, within the 2 retries");
        assertEquals(3, requests.get());
    }

    @Test
    void ensureCircuitOpensAfterRepeatedFailuresAndThePeerIsSkipped() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        String failing = peer("node-1", requests, Integer.MAX_VALUE, 200, "{}");
        PeerClient client = client(List.of(failing));

        for (int i = 0; i < 3; i++) {
            client.getFirst("/api/things/x", new HttpHeaders());
        }
        int afterThreeFailures = requests.get();
        PeerClient.PeerLookup skipped = client.getFirst("/api/things/x", new HttpHeaders());

        assertEquals(9, afterThreeFailures, "3 requests x (1 attempt + 2 retries)");
        assertEquals(afterThreeFailures, requests.get(), "the open circuit skips the peer");
        assertEquals(List.of(failing), skipped.unreachablePeers());
    }

    @Test
    void ensurePeerThatSendsARequestIsAskedAgainAtOnce() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        PeerClient client = client(List.of(peer("node-1", requests, 9, 200, "{}")));
        for (int i = 0; i < 3; i++) {
            client.getFirst("/api/things/x", new HttpHeaders());
        }
        assertEquals(List.of(client.peers().get(0)), client.getFirst("/api/things/x", new HttpHeaders()).unreachablePeers(),
                "circuit open after 3 failed requests");

        client.heardFrom("127.0.0.1");   // the peer's host name is its node id
        PeerClient.PeerLookup lookup = client.getFirst("/api/things/x", new HttpHeaders());

        assertTrue(lookup.found().isPresent(), "asked again, without waiting for the circuit to time out");
        assertEquals(10, requests.get());
    }

    @Test
    void ensureGetFromAllReturnsOneAnswerPerPeer() throws IOException {
        String dead = deadPeer();
        PeerClient client = client(List.of(peer("node-1", 200, "[1]"), peer("node-2", 200, "[2]"), dead));

        List<PeerClient.PeerResponse> responses = client.getFromAll("/internal/things", new HttpHeaders());

        assertEquals(3, responses.size());
        assertEquals(2, responses.stream().filter(PeerClient.PeerResponse::found).count());
        assertEquals(1, responses.stream().filter(PeerClient.PeerResponse::unreachable).count());
    }

    @Test
    void ensureReplicaLeavesItselfOutOfThePeerList() {
        PeerClient client = new PeerClient("am-1",
                List.of("http://am-0.am.ns.svc:8081", "http://am-1.am.ns.svc:8081", "http://am-10.am.ns.svc:8081"),
                300, 500, 2, 10, 3, 60_000, 2_000);

        assertTrue(PeerClient.isSelf("http://am-1.am.ns.svc:8081", "am-1"));
        assertTrue(PeerClient.isSelf("http://am-1:8081", "am-1"));
        assertFalse(PeerClient.isSelf("http://am-10.am.ns.svc:8081", "am-1"));
        assertFalse(PeerClient.isSelf("http://am-0.am.ns.svc:8081", "am-1"));
        assertTrue(client.hasPeers());
    }

    @Test
    void ensureEncodedPathIsSentAsIsNotEncodedTwice() throws IOException {
        PeerClient client = client(List.of(peer("node-1", 204, "")));

        client.send(client.peers().get(0), HttpMethod.PUT,
                "/internal/replicas/models?key=Airbus%20A320neo", new HttpHeaders(), "{}".getBytes(StandardCharsets.UTF_8));
        client.getFirst("/api/models/Airbus%20A320neo?x=a%2Fb", new HttpHeaders());

        assertEquals(List.of("/internal/replicas/models?key=Airbus%20A320neo", "/api/models/Airbus%20A320neo?x=a%2Fb"),
                List.copyOf(rawPaths));
    }

    @Test
    void ensureForwardedWriteWaitsLongerThanOtherRequests() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                Thread.sleep(1_000);   // longer than the 500 ms read timeout, shorter than the 2 s for writes
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        servers.add(server);
        String slow = "http://127.0.0.1:" + server.getAddress().getPort();
        PeerClient client = client(List.of(slow));

        assertTrue(client.send(slow, HttpMethod.PUT, "/x", new HttpHeaders(), null).unreachable());
        assertEquals(204, client.forwardWrite(slow, HttpMethod.DELETE, "/x", new HttpHeaders(), null).status().value());
    }

    @Test
    void ensureClientWithoutPeersReportsSo() {
        PeerClient client = client(List.of(" ", ""));

        assertFalse(client.hasPeers());
        assertEquals("node-0", client.nodeId());
        assertTrue(client.getFirst("/x", new HttpHeaders()).found().isEmpty());
    }
}
