package aisafe.shared.infrastructure.replication;

import aisafe.shared.infrastructure.peers.PeerClient;
import aisafe.shared.infrastructure.security.ServiceTokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Delivers the replication commands of the outbox, oldest first, with the replica's own service
 * token. A command that fails is retried later with exponential backoff, and the commands after
 * it for the same peer wait, so every peer receives the changes in order.
 */
@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    /** After this many failed attempts a command is set aside, so it cannot block the peer's queue forever. */
    static final int MAX_ATTEMPTS = 20;

    private final OutboxRepository outbox;
    private final ReplicaRing ring;
    private final PeerClient peerClient;
    private final ServiceTokens serviceTokens;
    /** Commands being sent right now, by either path: a command is never sent twice at once. */
    private final Set<Long> sending = ConcurrentHashMap.newKeySet();

    public OutboxDispatcher(OutboxRepository outbox, ReplicaRing ring, PeerClient peerClient, ServiceTokens serviceTokens) {
        this.outbox = outbox;
        this.ring = ring;
        this.peerClient = peerClient;
        this.serviceTokens = serviceTokens;
    }

    @Scheduled(fixedDelayString = "${aisafe.replication.dispatch-interval-ms:500}")
    public void dispatch() {
        if (!ring.isEnabled()) {
            return;
        }
        Set<String> waiting = new HashSet<>();
        Instant now = Instant.now();
        for (OutboxEntry entry : outbox.findTop200ByOrderByIdAsc()) {
            String target = entry.getTargetNode();
            if (waiting.contains(target)) {
                continue;
            }
            if (entry.getNextAttemptAt().isAfter(now)) {
                waiting.add(target);
                continue;
            }
            Optional<ReplicaRing.Member> member = ring.member(target);
            if (member.isEmpty()) {
                log.warn("Replication command {} dropped: {} is not a replica of this service", entry.getId(), target);
                outbox.delete(entry);
                continue;
            }
            if (!deliverOnce(entry, member.get())) {
                waiting.add(target);
            }
        }
    }

    /**
     * Delivers these commands now, once each (see {@link Replicator}). It runs after the change
     * that wrote them was committed, so in a transaction of its own. A command that fails stays
     * in the outbox for {@link #dispatch()}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliverNow(List<OutboxEntry> entries) {
        if (!ring.isEnabled()) {
            return;
        }
        entries.forEach(entry -> ring.member(entry.getTargetNode()).ifPresent(member -> deliverOnce(entry, member)));
    }

    /** Delivers the command unless the other path is already sending it (then it counts as waiting). */
    private boolean deliverOnce(OutboxEntry entry, ReplicaRing.Member member) {
        if (!sending.add(entry.getId())) {
            return false;
        }
        try {
            return deliver(entry, member);
        } finally {
            sending.remove(entry.getId());
        }
    }

    private boolean deliver(OutboxEntry entry, ReplicaRing.Member member) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(serviceTokens.current());
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpMethod method = HttpMethod.valueOf(entry.getMethod());
        byte[] body = entry.getBody() == null ? null : entry.getBody().getBytes(StandardCharsets.UTF_8);
        PeerClient.PeerResponse response = peerClient.send(member.url(), method, entry.getPath(), headers, body);
        boolean delivered = !response.unreachable() && response.status().is2xxSuccessful();
        if (delivered) {
            outbox.delete(entry);
            return true;
        }
        String error = response.unreachable()
                ? "unreachable"
                : response.status().value() + " " + new String(response.body(), StandardCharsets.UTF_8);
        entry.failed(Instant.now(), error);
        if (entry.getAttempts() >= MAX_ATTEMPTS) {
            log.error("Replication {} {} to {} given up after {} attempts: {}",
                    entry.getMethod(), entry.getPath(), member.nodeId(), entry.getAttempts(), error);
            outbox.delete(entry);
            return true;
        }
        outbox.save(entry);
        log.warn("Replication {} {} to {} failed (attempt {}): {}; retrying later",
                entry.getMethod(), entry.getPath(), member.nodeId(), entry.getAttempts(), error);
        return false;
    }
}
