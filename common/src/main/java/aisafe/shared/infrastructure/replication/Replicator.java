package aisafe.shared.infrastructure.replication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Called by the persistence adapters after they save or delete a replicated entity. It stamps
 * the change (time and this replica), remembers it in {@link ReplicaState} and writes the
 * replication commands to the outbox, in the same transaction as the change; the
 * {@link OutboxDispatcher} delivers them.
 *
 * <ul>
 *   <li>a catalog entity goes to every other replica;</li>
 *   <li>a record goes to the other holder: created here, it goes to this replica's successor;
 *   changed on a holder, it goes to the other holder.</li>
 * </ul>
 *
 * <p>A deletion is sent like a save, as a tombstone (no payload). While a replica is being
 * applied (a change received from a peer), nothing is sent again, so changes never bounce
 * between replicas.</p>
 */
@Component
public class Replicator {

    private static final ThreadLocal<Boolean> APPLYING = ThreadLocal.withInitial(() -> false);

    private static final Logger log = LoggerFactory.getLogger(Replicator.class);

    private final ReplicaRing ring;
    private final OutboxRepository outbox;
    private final OutboxDispatcher dispatcher;
    private final ReplicaStateRepository states;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public Replicator(ReplicaRing ring, OutboxRepository outbox, OutboxDispatcher dispatcher,
                      ReplicaStateRepository states, ObjectMapper objectMapper) {
        this(ring, outbox, dispatcher, states, objectMapper, Clock.systemUTC());
    }

    Replicator(ReplicaRing ring, OutboxRepository outbox, OutboxDispatcher dispatcher,
               ReplicaStateRepository states, ObjectMapper objectMapper, Clock clock) {
        this.ring = ring;
        this.outbox = outbox;
        this.dispatcher = dispatcher;
        this.states = states;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void catalogSaved(String type, String key, Object payload) {
        changed(type, key, null, null, objectMapper.valueToTree(payload), false);
    }

    public void catalogDeleted(String type, String key) {
        changed(type, key, null, null, null, true);
    }

    public void recordSaved(String type, String key, Object payload, Long version) {
        changed(type, key, true, version, objectMapper.valueToTree(payload), false);
    }

    public void recordDeleted(String type, String key) {
        changed(type, key, true, null, null, true);
    }

    /**
     * @param record true for a record (held by its home and the next replica), null for a catalog
     */
    private void changed(String type, String key, Boolean record, Long version, JsonNode payload, boolean deleted) {
        if (!sending()) {
            return;
        }
        ReplicaState state = states.findById(ReplicaState.idOf(type, key)).orElseGet(() -> new ReplicaState(type, key));
        String home = record == null ? null : (state.getHome() != null ? state.getHome() : ring.self());
        // Later than any change known here, even if this replica's clock is behind
        long stamp = Math.max(clock.millis(), state.getStamp() + 1);
        state.changed(home, stamp, ring.self(), deleted);
        states.save(state);

        String body = objectMapper.writeValueAsString(
                new ReplicaEnvelope(key, home, version, payload, stamp, ring.self(), deleted));
        List<String> targets = record == null
                ? ring.others().stream().map(ReplicaRing.Member::nodeId).toList()
                : ring.holdersOf(home).stream().filter(node -> !node.equals(ring.self())).toList();
        List<OutboxEntry> entries = targets.stream()
                .map(node -> outbox.save(new OutboxEntry(node, "PUT", path(type, key), body)))
                .toList();
        deliverWhenCommitted(entries);
    }

    /**
     * Sends the copies as soon as the change is committed, before the client gets its answer: a
     * replica that stops right after answering has already handed the change to the others. A copy
     * that cannot be delivered stays in the outbox and is retried in the background.
     */
    private void deliverWhenCommitted(List<OutboxEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatcher.deliverNow(entries);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    dispatcher.deliverNow(entries);
                } catch (RuntimeException e) {
                    // The change is committed: the copies stay in the outbox, and the client still gets its answer
                    log.warn("Copies of a change not sent right away ({}); sent from the outbox later", e.toString());
                }
            }
        });
    }

    /** Runs {@code action} as the application of a replica received from a peer. */
    public <T> T asReplica(Supplier<T> action) {
        boolean previous = APPLYING.get();
        APPLYING.set(true);
        try {
            return action.get();
        } finally {
            APPLYING.set(previous);
        }
    }

    public boolean isApplyingReplica() {
        return APPLYING.get();
    }

    /** The home of a record known here (live or deleted). */
    public Optional<String> homeOf(String type, String key) {
        return states.findById(ReplicaState.idOf(type, key)).map(ReplicaState::getHome);
    }

    private boolean sending() {
        return ring.isEnabled() && !isApplyingReplica();
    }

    static String path(String type, String key) {
        return "/internal/replicas/" + type + "?key=" + UriUtils.encodeQueryParam(key, StandardCharsets.UTF_8);
    }
}
