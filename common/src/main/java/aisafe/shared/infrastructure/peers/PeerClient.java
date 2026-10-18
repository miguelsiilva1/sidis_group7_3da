package aisafe.shared.infrastructure.peers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HTTP client for the other replicas of the same service (peer-to-peer, unicast).
 *
 * <p>Every peer is asked in parallel. Each request has a connection and a read timeout, is
 * retried with exponential backoff when the peer cannot be reached or fails (5xx), and goes
 * through a per-peer circuit breaker: after several consecutive failures the peer is skipped
 * for a while instead of slowing every request down. A 404 from a peer is an answer, not a
 * failure. Every attempt is logged, so the forwarding can be followed in the logs.</p>
 */
@Component
public class PeerClient {

    private static final Logger log = LoggerFactory.getLogger(PeerClient.class);

    private final String nodeId;
    private final List<String> peers;
    private final int retries;
    private final long backoffMs;
    private final int breakerFailures;
    private final Duration breakerOpenFor;
    private final RestClient restClient;
    /** For forwarded writes: the peer may itself ask its peers (one down costs ~1 s each) before answering. */
    private final RestClient writeClient;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, BreakerState> breakers = new ConcurrentHashMap<>();

    public PeerClient(@Value("${aisafe.node.id}") String nodeId,
                      @Value("${aisafe.peers:}") List<String> peers,
                      @Value("${aisafe.peers.connect-timeout-ms:500}") long connectTimeoutMs,
                      @Value("${aisafe.peers.read-timeout-ms:2000}") long readTimeoutMs,
                      @Value("${aisafe.peers.retries:1}") int retries,
                      @Value("${aisafe.peers.backoff-ms:100}") long backoffMs,
                      @Value("${aisafe.peers.breaker.failures:3}") int breakerFailures,
                      @Value("${aisafe.peers.breaker.open-ms:10000}") long breakerOpenMs,
                      @Value("${aisafe.peers.write-timeout-ms:10000}") long writeTimeoutMs) {
        this.nodeId = nodeId;
        this.peers = peers.stream()
                .map(String::trim)
                .filter(p -> !p.isEmpty())
                .filter(p -> !isSelf(p, nodeId))
                .toList();
        this.retries = retries;
        this.backoffMs = backoffMs;
        this.breakerFailures = breakerFailures;
        this.breakerOpenFor = Duration.ofMillis(breakerOpenMs);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        JdkClientHttpRequestFactory writeRequestFactory = new JdkClientHttpRequestFactory(httpClient);
        writeRequestFactory.setReadTimeout(Duration.ofMillis(writeTimeoutMs));
        this.writeClient = RestClient.builder().requestFactory(writeRequestFactory).build();
    }

    /**
     * Every replica can be given the same peer list, itself included: a peer whose host name is
     * this replica's node id (e.g. {@code http://aircraft-maintenance-0.aircraft-maintenance...}
     * for node {@code aircraft-maintenance-0}) is left out, so a replica never forwards to itself.
     */
    static boolean isSelf(String peer, String nodeId) {
        String host = URI.create(peer).getHost();
        return host != null && (host.equals(nodeId) || host.startsWith(nodeId + "."));
    }

    public String nodeId() {
        return nodeId;
    }

    public boolean hasPeers() {
        return !peers.isEmpty();
    }

    /**
     * Sends {@code GET path} to every peer and waits for all of them.
     *
     * @param path    path and query string, starting with "/", already URL-encoded (used as is)
     * @param headers headers to send (the forwarding headers are added here)
     * @return one result per peer
     */
    public List<PeerResponse> getFromAll(String path, HttpHeaders headers) {
        List<CompletableFuture<PeerResponse>> futures = askAll(path, headers);
        List<PeerResponse> responses = new ArrayList<>();
        futures.forEach(f -> responses.add(f.join()));
        return responses;
    }

    /**
     * Sends {@code GET path} to every peer in parallel and returns the first 200 that arrives.
     *
     * @return the first peer that has the resource, if any, and the peers that could not answer
     */
    public PeerLookup getFirst(String path, HttpHeaders headers) {
        List<CompletableFuture<PeerResponse>> futures = askAll(path, headers);
        CompletableFuture<PeerResponse> firstFound = new CompletableFuture<>();
        AtomicInteger pending = new AtomicInteger(futures.size());
        for (CompletableFuture<PeerResponse> future : futures) {
            future.thenAccept(response -> {
                if (response.found()) {
                    firstFound.complete(response);
                } else if (pending.decrementAndGet() == 0) {
                    firstFound.complete(null);
                }
            });
        }
        if (futures.isEmpty()) {
            firstFound.complete(null);
        }
        PeerResponse found = firstFound.join();
        List<String> unreachable = futures.stream()
                .filter(CompletableFuture::isDone)
                .map(CompletableFuture::join)
                .filter(PeerResponse::unreachable)
                .map(PeerResponse::peer)
                .toList();
        return new PeerLookup(Optional.ofNullable(found), found == null ? unreachable : List.of());
    }

    /**
     * Asks every peer in parallel. The logging context (replica, correlation id) goes with each
     * request, so the forwarding log lines can be matched to the client request.
     */
    private List<CompletableFuture<PeerResponse>> askAll(String path, HttpHeaders headers) {
        Map<String, String> loggingContext = MDC.getCopyOfContextMap();
        return peers.stream()
                .map(peer -> CompletableFuture.supplyAsync(() -> {
                    if (loggingContext != null) {
                        MDC.setContextMap(loggingContext);
                    }
                    try {
                        return get(peer, path, headers);
                    } finally {
                        MDC.clear();
                    }
                }, executor))
                .toList();
    }

    private PeerResponse get(String peer, String path, HttpHeaders headers) {
        if (isOpen(peer)) {
            log.warn("Peer {} skipped for GET {}: circuit open after repeated failures", peer, path);
            return PeerResponse.unreachable(peer);
        }
        for (int attempt = 0; attempt <= retries; attempt++) {
            if (attempt > 0) {
                sleep(backoffMs << (attempt - 1));
            }
            try {
                PeerResponse response = restClient.get()
                        .uri(URI.create(peer + path))
                        .headers(h -> {
                            h.addAll(headers);
                            h.set(PeerHeaders.FORWARDED, nodeId);
                        })
                        .accept(MediaType.APPLICATION_JSON, MediaType.valueOf("application/hal+json"))
                        .exchange((request, response1) -> toPeerResponse(peer, response1.getStatusCode(),
                                response1.getHeaders(), response1.getBody().readAllBytes()), true);
                if (!response.status().is5xxServerError()) {
                    log.info("GET {} forwarded to peer {} (attempt {}): {}", path, peer, attempt + 1, response.status().value());
                    recordSuccess(peer);
                    return response;
                }
                log.warn("GET {} forwarded to peer {} (attempt {}): {}", path, peer, attempt + 1, response.status().value());
            } catch (RuntimeException e) {
                log.warn("GET {} forwarded to peer {} (attempt {}) failed: {}", path, peer, attempt + 1, rootMessage(e));
            }
        }
        recordFailure(peer);
        return PeerResponse.unreachable(peer);
    }

    /**
     * Sends one request to one peer, once (no retries: the caller decides what to do on failure).
     * Used to forward writes and to deliver replication commands.
     *
     * @return the peer's answer, or an unreachable result if it could not be reached
     */
    public PeerResponse send(String peer, HttpMethod method, String path, HttpHeaders headers, byte[] body) {
        return send(restClient, peer, method, path, headers, body);
    }

    /**
     * Forwards a client's write to one peer, once. It waits longer for the answer than
     * {@link #send}: giving up early would tell the client the write failed when the peer may
     * still be doing it.
     */
    public PeerResponse forwardWrite(String peer, HttpMethod method, String path, HttpHeaders headers, byte[] body) {
        return send(writeClient, peer, method, path, headers, body);
    }

    private PeerResponse send(RestClient client, String peer, HttpMethod method, String path, HttpHeaders headers, byte[] body) {
        if (isOpen(peer)) {
            log.warn("Peer {} skipped for {} {}: circuit open after repeated failures", peer, method, path);
            return PeerResponse.unreachable(peer);
        }
        try {
            RestClient.RequestBodySpec request = client.method(method)
                    .uri(URI.create(peer + path))
                    .headers(h -> {
                        h.addAll(headers);
                        h.set(PeerHeaders.FORWARDED, nodeId);
                    });
            if (body != null && body.length > 0) {
                request = request.body(body);
            }
            PeerResponse response = request.exchange((req, res) -> toPeerResponse(peer, res.getStatusCode(),
                    res.getHeaders(), res.getBody().readAllBytes()), true);
            log.info("{} {} sent to peer {}: {}", method, path, peer, response.status().value());
            if (response.status().is5xxServerError()) {
                recordFailure(peer);
            } else {
                recordSuccess(peer);
            }
            return response;
        } catch (RuntimeException e) {
            log.warn("{} {} sent to peer {} failed: {}", method, path, peer, rootMessage(e));
            recordFailure(peer);
            return PeerResponse.unreachable(peer);
        }
    }

    public List<String> peers() {
        return peers;
    }

    /**
     * A request from a peer proves it is up again (e.g. it just restarted and asked for its data):
     * its circuit is closed at once, instead of skipping it until the circuit times out.
     */
    public void heardFrom(String peerNodeId) {
        peers.stream()
                .filter(peer -> isSelf(peer, peerNodeId))
                .filter(peer -> breakers.remove(peer) != null)
                .forEach(peer -> log.info("Peer {} sent a request: circuit closed", peer));
    }

    private static PeerResponse toPeerResponse(String peer, HttpStatusCode status, HttpHeaders headers, byte[] body) {
        return new PeerResponse(peer, status, headers, body, false);
    }

    private boolean isOpen(String peer) {
        BreakerState state = breakers.get(peer);
        return state != null && state.openUntil != null && Instant.now().isBefore(state.openUntil);
    }

    private void recordSuccess(String peer) {
        breakers.remove(peer);
    }

    private void recordFailure(String peer) {
        BreakerState state = breakers.computeIfAbsent(peer, p -> new BreakerState());
        synchronized (state) {
            state.failures++;
            if (state.failures >= breakerFailures) {
                state.openUntil = Instant.now().plus(breakerOpenFor);
                state.failures = 0;
                log.warn("Circuit opened for peer {} for {} ms", peer, breakerOpenFor.toMillis());
            }
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root instanceof IOException || root.getMessage() == null
                ? root.getClass().getSimpleName()
                : root.getMessage();
    }

    private static final class BreakerState {
        private int failures;
        private Instant openUntil;
    }

    /**
     * Answer of one peer. {@code unreachable} means the peer did not answer at all.
     */
    public record PeerResponse(String peer, HttpStatusCode status, HttpHeaders headers, byte[] body,
                               boolean unreachable) {
        static PeerResponse unreachable(String peer) {
            return new PeerResponse(peer, null, HttpHeaders.EMPTY, new byte[0], true);
        }

        /** An answer meaning the peer could not be reached (also for tests). */
        public static PeerResponse unreachableFor(String peer) {
            return unreachable(peer);
        }

        public boolean found() {
            return !unreachable && status.is2xxSuccessful();
        }
    }

    /**
     * Result of looking a resource up on the peers.
     */
    public record PeerLookup(Optional<PeerResponse> found, List<String> unreachablePeers) {
    }
}
