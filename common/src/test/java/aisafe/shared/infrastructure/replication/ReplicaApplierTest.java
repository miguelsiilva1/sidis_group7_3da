package aisafe.shared.infrastructure.replication;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ReplicaApplierTest {

    record Thing(String name) {}

    private final JsonMapper json = JsonMapper.builder().build();
    private final Map<String, ReplicaState> stored = new HashMap<>();
    @SuppressWarnings("unchecked")
    private final ReplicaHandler<Thing> handler = mock(ReplicaHandler.class);
    private ReplicaApplier applier;

    @BeforeEach
    void setUp() {
        ReplicaStateRepository states = mock(ReplicaStateRepository.class);
        when(states.findById(any())).thenAnswer(i -> Optional.ofNullable(stored.get(i.<String>getArgument(0))));
        when(states.save(any())).thenAnswer(i -> {
            ReplicaState state = i.getArgument(0);
            stored.put(state.getId(), state);
            return state;
        });
        Replicator replicator = new Replicator(new ReplicaRing("am-0", List.of()), mock(OutboxRepository.class),
                mock(OutboxDispatcher.class), states, json);
        applier = new ReplicaApplier(replicator, states, json);
        when(handler.type()).thenReturn("things");
        when(handler.payloadType()).thenReturn(Thing.class);
        when(handler.isCatalog()).thenReturn(false);
    }

    private ReplicaEnvelope saved(String value, long stamp, String node) {
        return new ReplicaEnvelope("k", "am-1", 1L, json.valueToTree(new Thing(value)), stamp, node, false);
    }

    private ReplicaEnvelope deleted(long stamp, String node) {
        return new ReplicaEnvelope("k", "am-1", null, null, stamp, node, true);
    }

    @Test
    void ensureLaterChangeIsAppliedAndRemembered() {
        assertTrue(applier.apply(handler, saved("a", 100, "am-1")));

        verify(handler).apply("k", new Thing("a"), 1L);
        assertEquals(100, stored.get("things:k").getStamp());
        assertEquals("am-1", stored.get("things:k").getHome());
    }

    @Test
    void ensureRepeatedOrOlderChangeIsIgnored() {
        applier.apply(handler, saved("new", 200, "am-1"));

        assertFalse(applier.apply(handler, saved("new", 200, "am-1")));
        assertFalse(applier.apply(handler, saved("old", 100, "am-2")));
        verify(handler, times(1)).apply(anyString(), any(), any());
    }

    @Test
    void ensureConcurrentChangesWithTheSameTimeAreDecidedByReplicaSoEveryReplicaAgrees() {
        applier.apply(handler, saved("from-2", 100, "am-2"));

        assertFalse(applier.apply(handler, saved("from-1", 100, "am-1")));
        assertTrue(applier.apply(handler, saved("from-3", 100, "am-3")));
    }

    @Test
    void ensureTombstoneDeletesAndBlocksAStaleCopy() {
        applier.apply(handler, saved("a", 100, "am-1"));

        assertTrue(applier.apply(handler, deleted(200, "am-1")));
        assertFalse(applier.apply(handler, saved("a", 100, "am-1")), "a copy that missed the deletion does not bring it back");

        verify(handler).delete("k");
        assertTrue(stored.get("things:k").isDeleted());
    }

    @Test
    void ensureSavingAgainAfterTheDeletionWins() {
        applier.apply(handler, deleted(200, "am-1"));

        assertTrue(applier.apply(handler, saved("again", 300, "am-2")));
        assertFalse(stored.get("things:k").isDeleted());
    }
}
