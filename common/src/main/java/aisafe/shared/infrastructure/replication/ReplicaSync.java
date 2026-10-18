package aisafe.shared.infrastructure.replication;

import aisafe.shared.infrastructure.peers.PeerClient;
import aisafe.shared.infrastructure.security.ServiceTokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Synchronisation with the peers (anti-entropy): this replica asks each peer for the catalogs
 * and for the records it should hold (as their home or as the home's successor), tombstones
 * included, and applies every change later than the one it knows.
 *
 * <ul>
 *   <li><b>On startup</b>, before the replica accepts traffic (it runs before any other startup
 *   task, and the replica only reports itself ready once it has finished). A replica with a new,
 *   empty disk (e.g. reborn on another VM) gets its data back from its peers.</li>
 *   <li><b>Every {@code aisafe.replication.sync-interval-ms}</b> (30 s by default). It restores a
 *   copy that is missing because a peer did not answer on startup, or because a replication
 *   command was given up after too many failed attempts.</li>
 * </ul>
 *
 * <p>Peers that do not answer are skipped and asked again in the next round.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReplicaSync implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReplicaSync.class);

    private final List<ReplicaHandler<?>> handlers;
    private final ReplicaRing ring;
    private final PeerClient peerClient;
    private final ServiceTokens serviceTokens;
    private final ReplicaApplier applier;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean started = new AtomicBoolean(false);

    public ReplicaSync(List<ReplicaHandler<?>> handlers, ReplicaRing ring, PeerClient peerClient,
                       ServiceTokens serviceTokens, ReplicaApplier applier, ObjectMapper objectMapper) {
        this.handlers = handlers;
        this.ring = ring;
        this.peerClient = peerClient;
        this.serviceTokens = serviceTokens;
        this.applier = applier;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        syncWithPeers(true);
        started.set(true);
    }

    @Scheduled(initialDelayString = "${aisafe.replication.sync-interval-ms:30000}",
               fixedDelayString = "${aisafe.replication.sync-interval-ms:30000}")
    public void periodicSync() {
        // Not before the startup round has finished: both would apply the same changes
        if (started.get()) {
            syncWithPeers(false);
        }
    }

    void syncWithPeers(boolean onStartup) {
        if (!ring.isEnabled() || handlers.isEmpty()) {
            return;
        }
        JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, ReplicaEnvelope.class);
        handlers.stream().sorted(Comparator.comparingInt(ReplicaHandler::order)).forEach(handler -> {
            for (ReplicaRing.Member peer : ring.others()) {
                syncFrom(handler, peer, listType, onStartup);
            }
        });
    }

    private void syncFrom(ReplicaHandler<?> handler, ReplicaRing.Member peer, JavaType listType, boolean onStartup) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(serviceTokens.current());
        String path = "/internal/replicas/" + handler.type() + "?for=" + ring.self();
        PeerClient.PeerResponse response = peerClient.send(peer.url(), HttpMethod.GET, path, headers, null);
        if (!response.found()) {
            if (onStartup) {
                log.warn("Sync of {} from {} on startup: peer did not answer, skipped", handler.type(), peer.nodeId());
            }
            return;
        }
        List<ReplicaEnvelope> changes;
        try {
            changes = objectMapper.readValue(response.body(), listType);
        } catch (RuntimeException e) {
            log.warn("Sync of {} from {}: unreadable answer, skipped: {}", handler.type(), peer.nodeId(), e.getMessage());
            return;
        }
        int applied = 0;
        for (ReplicaEnvelope change : changes) {
            try {
                if (applier.apply(handler, change)) {
                    applied++;
                }
            } catch (RuntimeException e) {
                log.warn("Sync of {} {} from {} failed: {}", handler.type(), change.key(), peer.nodeId(), e.getMessage());
            }
        }
        if (onStartup || applied > 0) {
            log.info("Sync of {} from {}{}: {} of {} applied", handler.type(), peer.nodeId(),
                    onStartup ? " on startup" : "", applied, changes.size());
        }
    }
}
