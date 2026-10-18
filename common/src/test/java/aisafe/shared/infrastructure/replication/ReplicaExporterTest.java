package aisafe.shared.infrastructure.replication;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReplicaExporterTest {

    private static final List<String> PEERS = List.of("http://am-0.am:8081", "http://am-1.am:8081", "http://am-2.am:8081");

    record Thing(String name) {}

    @SuppressWarnings("unchecked")
    private final ReplicaHandler<Thing> handler = mock(ReplicaHandler.class);
    private final ReplicaStateRepository states = mock(ReplicaStateRepository.class);
    private ReplicaExporter exporter;

    private static ReplicaState state(String key, String home, long stamp, boolean deleted) {
        ReplicaState state = new ReplicaState("things", key);
        state.changed(home, stamp, home, deleted);
        return state;
    }

    @BeforeEach
    void setUp() {
        when(handler.type()).thenReturn("things");
        when(handler.isCatalog()).thenReturn(false);
        // Held by am-0: "own" (home am-0, copy on am-1), "copy" (home am-2, copy here),
        // and the tombstone of "gone" (home am-0)
        when(handler.exportAll()).thenReturn(List.of(
                new ReplicaHandler.Item<>("own", new Thing("own"), 1L),
                new ReplicaHandler.Item<>("copy", new Thing("copy"), 2L)));
        when(states.findByType("things")).thenReturn(List.of(
                state("own", "am-0", 10, false), state("copy", "am-2", 20, false), state("gone", "am-0", 30, true)));
        exporter = new ReplicaExporter(states, new ReplicaRing("am-0", PEERS), JsonMapper.builder().build());
    }

    private static List<String> keys(List<ReplicaEnvelope> envelopes) {
        return envelopes.stream().map(ReplicaEnvelope::key).toList();
    }

    @Test
    void ensurePeerGetsOnlyTheRecordsItShouldHoldTombstonesIncluded() {
        assertEquals(List.of("own", "gone"), keys(exporter.export(handler, "am-1")));
        assertEquals(List.of("copy"), keys(exporter.export(handler, "am-2")));
    }

    @Test
    void ensureWithoutRecipientEverythingHeldHereIsExportedWithStamps() {
        List<ReplicaEnvelope> all = exporter.export(handler, null);

        assertEquals(List.of("own", "copy", "gone"), keys(all));
        assertEquals(List.of(10L, 20L, 30L), all.stream().map(ReplicaEnvelope::stamp).toList());
        assertEquals(List.of(false, false, true), all.stream().map(ReplicaEnvelope::deleted).toList());
        assertNull(all.get(2).payload());
    }

    @Test
    void ensureEntityWithoutStateIsExportedAsHomedHereWithNoStamp() {
        when(states.findByType("things")).thenReturn(List.of());

        ReplicaEnvelope own = exporter.export(handler, null).get(0);

        assertEquals("am-0", own.home());
        assertEquals(0L, own.stamp());
    }
}
