package aisafe.shared.infrastructure.replication;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What this replica holds of one kind of entity, as envelopes: the live entities with the stamp
 * of their last change, and the tombstones of the deleted ones.
 */
@Component
public class ReplicaExporter {

    private final ReplicaStateRepository states;
    private final ReplicaRing ring;
    private final ObjectMapper objectMapper;

    public ReplicaExporter(ReplicaStateRepository states, ReplicaRing ring, ObjectMapper objectMapper) {
        this.states = states;
        this.ring = ring;
        this.objectMapper = objectMapper;
    }

    /**
     * @param forNode a peer: only what it should hold (catalogs: everything; records: those whose
     *                home is that peer or the peer before it). Null: everything held here.
     */
    public <T> List<ReplicaEnvelope> export(ReplicaHandler<T> handler, String forNode) {
        Map<String, ReplicaState> known = states.findByType(handler.type()).stream()
                .collect(Collectors.toMap(ReplicaState::getKey, Function.identity()));
        List<ReplicaEnvelope> envelopes = new ArrayList<>();
        for (ReplicaHandler.Item<T> item : handler.exportAll()) {
            Optional<ReplicaState> state = Optional.ofNullable(known.get(item.key()));
            if (state.map(ReplicaState::isDeleted).orElse(false)) {
                continue; // the tombstone below is the latest change
            }
            String home = handler.isCatalog() ? null : state.map(ReplicaState::getHome).orElse(ring.self());
            envelopes.add(new ReplicaEnvelope(item.key(), home, item.version(), objectMapper.valueToTree(item.payload()),
                    state.map(ReplicaState::getStamp).orElse(0L), state.map(ReplicaState::getStampNode).orElse(""), false));
        }
        for (ReplicaState tombstone : known.values().stream().filter(ReplicaState::isDeleted).toList()) {
            envelopes.add(new ReplicaEnvelope(tombstone.getKey(), tombstone.getHome(), null, null,
                    tombstone.getStamp(), tombstone.getStampNode(), true));
        }
        return envelopes.stream()
                .filter(e -> forNode == null || handler.isCatalog() || e.home() == null || ring.holdersOf(e.home()).contains(forNode))
                .toList();
    }
}
