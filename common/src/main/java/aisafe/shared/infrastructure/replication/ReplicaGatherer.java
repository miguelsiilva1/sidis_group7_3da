package aisafe.shared.infrastructure.replication;

import aisafe.shared.infrastructure.peers.ForwardingHeaders;
import aisafe.shared.infrastructure.peers.PeerClient;
import aisafe.shared.infrastructure.peers.UnreachablePeers;
import aisafe.shared.infrastructure.security.ServiceTokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Gathers every entity of one kind held by any replica, for listings and reports (D5: the
 * replica that received the request collects the raw data and computes the result once).
 *
 * <p>Records are partitioned and each one is held by two replicas, so the same record usually
 * arrives twice: only its latest change is kept (the latest stamp), and a record whose latest
 * change is a deletion is left out, even if a stale copy still has it. Peers that do not answer are reported in
 * {@code X-Unreachable-Peers}; the result is marked partial only when both holders of some
 * records did not answer, since otherwise their copy came from the other holder.</p>
 */
@Component
public class ReplicaGatherer {

    private static final Logger log = LoggerFactory.getLogger(ReplicaGatherer.class);

    private final ReplicaRing ring;
    private final PeerClient peerClient;
    private final ServiceTokens serviceTokens;
    private final ReplicaExporter exporter;
    private final ObjectMapper objectMapper;

    public ReplicaGatherer(ReplicaRing ring, PeerClient peerClient, ServiceTokens serviceTokens,
                           ReplicaExporter exporter, ObjectMapper objectMapper) {
        this.ring = ring;
        this.peerClient = peerClient;
        this.serviceTokens = serviceTokens;
        this.exporter = exporter;
        this.objectMapper = objectMapper;
    }

    /**
     * @return every entity of the handler's kind held here or by the peers, one per key (its
     *         latest change), without the deleted ones, ordered by key
     */
    public <T> List<ReplicaHandler.Item<T>> gather(ReplicaHandler<T> handler) {
        Map<String, ReplicaEnvelope> byKey = new LinkedHashMap<>();
        exporter.export(handler, null).forEach(envelope -> keepLatest(byKey, envelope));
        // Also when the request was forwarded by a peer (e.g. a delete that checks whether the
        // entity is in use): exporting is local-only on every replica, so this never loops.
        if (ring.isEnabled()) {
            gatherFromPeers(handler, byKey);
        }
        return byKey.values().stream()
                .filter(envelope -> !envelope.deleted())
                .sorted(Comparator.comparing(ReplicaEnvelope::key))
                .map(envelope -> new ReplicaHandler.Item<>(envelope.key(),
                        objectMapper.treeToValue(envelope.payload(), handler.payloadType()), envelope.version()))
                .toList();
    }

    private void gatherFromPeers(ReplicaHandler<?> handler, Map<String, ReplicaEnvelope> byKey) {
        HttpHeaders headers = ForwardingHeaders.fromCurrentRequest();
        headers.setBearerAuth(serviceTokens.current());
        JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, ReplicaEnvelope.class);
        List<String> unreachable = new ArrayList<>();
        for (PeerClient.PeerResponse response : peerClient.getFromAll("/internal/replicas/" + handler.type(), headers)) {
            if (!response.found()) {
                if (!response.unreachable()) {
                    log.warn("Peer {} answered {} when asked for its {}", response.peer(), response.status().value(), handler.type());
                }
                unreachable.add(response.peer());
                continue;
            }
            try {
                List<ReplicaEnvelope> envelopes = objectMapper.readValue(response.body(), listType);
                envelopes.forEach(envelope -> keepLatest(byKey, envelope));
            } catch (RuntimeException e) {
                log.warn("Peer {} sent unreadable {}: {}", response.peer(), handler.type(), e.getMessage());
                unreachable.add(response.peer());
            }
        }
        if (!unreachable.isEmpty()) {
            UnreachablePeers.add(unreachable, handler.isCatalog() ? false : someRecordsMissing(unreachable));
        }
    }

    /**
     * A record is missing only when both its holders (its home and the next replica) did not
     * answer. This replica always answers, so with one peer down nothing is missing.
     */
    boolean someRecordsMissing(List<String> unreachablePeers) {
        Set<String> down = unreachablePeers.stream().map(ReplicaRing::nodeIdOf).collect(Collectors.toSet());
        return ring.others().stream()
                .map(ReplicaRing.Member::nodeId)
                .anyMatch(home -> down.containsAll(ring.holdersOf(home)));
    }

    private static void keepLatest(Map<String, ReplicaEnvelope> byKey, ReplicaEnvelope envelope) {
        byKey.merge(envelope.key(), envelope, (kept, other) -> isLater(other, kept) ? other : kept);
    }

    /** Later stamp; with equal stamps (or none), the higher version. */
    private static boolean isLater(ReplicaEnvelope a, ReplicaEnvelope b) {
        int byStamp = ReplicaState.compare(a.stamp() == null ? 0 : a.stamp(), a.stampNode(),
                b.stamp() == null ? 0 : b.stamp(), b.stampNode());
        if (byStamp != 0) {
            return byStamp > 0;
        }
        return (a.version() == null ? -1 : a.version()) > (b.version() == null ? -1 : b.version());
    }
}
