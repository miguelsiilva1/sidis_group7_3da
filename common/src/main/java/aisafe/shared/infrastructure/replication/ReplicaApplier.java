package aisafe.shared.infrastructure.replication;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Applies replicas received from peers, each in its own transaction and marked as a replica so
 * it is not sent on again. A change is applied only if it is later than the last change known
 * here for that entity (its stamp), so repeated, late or stale changes are ignored.
 */
@Component
public class ReplicaApplier {

    private final Replicator replicator;
    private final ReplicaStateRepository states;
    private final ObjectMapper objectMapper;

    public ReplicaApplier(Replicator replicator, ReplicaStateRepository states, ObjectMapper objectMapper) {
        this.replicator = replicator;
        this.states = states;
        this.objectMapper = objectMapper;
    }

    /** @return whether the change was applied (false: this replica already knew it or a later one) */
    @Transactional
    public <T> boolean apply(ReplicaHandler<T> handler, ReplicaEnvelope envelope) {
        String type = handler.type();
        ReplicaState state = states.findById(ReplicaState.idOf(type, envelope.key()))
                .orElseGet(() -> new ReplicaState(type, envelope.key()));
        if (!state.isOlderThan(envelope.stamp(), envelope.stampNode())) {
            return false;
        }
        replicator.asReplica(() -> {
            if (envelope.deleted()) {
                handler.delete(envelope.key());
            } else {
                handler.apply(envelope.key(), objectMapper.treeToValue(envelope.payload(), handler.payloadType()),
                        envelope.version());
            }
            return null;
        });
        state.changed(handler.isCatalog() ? null : envelope.home(), envelope.stamp(), envelope.stampNode(), envelope.deleted());
        states.save(state);
        return true;
    }
}
