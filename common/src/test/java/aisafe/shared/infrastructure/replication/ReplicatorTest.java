package aisafe.shared.infrastructure.replication;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReplicatorTest {

    private static final List<String> PEERS = List.of(
            "http://am-0.am:8081", "http://am-1.am:8081", "http://am-2.am:8081");
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC);

    record Thing(String name, int size) {}

    private OutboxRepository outbox;
    private OutboxDispatcher dispatcher;
    private ReplicaStateRepository states;
    private final Map<String, ReplicaState> stored = new HashMap<>();
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        outbox = mock(OutboxRepository.class);
        when(outbox.save(any())).thenAnswer(i -> i.getArgument(0));
        dispatcher = mock(OutboxDispatcher.class);
        states = mock(ReplicaStateRepository.class);
        when(states.findById(any())).thenAnswer(i -> Optional.ofNullable(stored.get(i.<String>getArgument(0))));
        when(states.save(any())).thenAnswer(i -> {
            ReplicaState state = i.getArgument(0);
            stored.put(state.getId(), state);
            return state;
        });
    }

    private Replicator replicator(String self, List<String> peers) {
        return new Replicator(new ReplicaRing(self, peers), outbox, dispatcher, states, json, CLOCK);
    }

    private List<OutboxEntry> sent() {
        ArgumentCaptor<OutboxEntry> captor = ArgumentCaptor.forClass(OutboxEntry.class);
        verify(outbox, atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    private ReplicaEnvelope envelopeOf(OutboxEntry entry) {
        return json.readValue(entry.getBody(), ReplicaEnvelope.class);
    }

    @Test
    void ensureCatalogChangeGoesToEveryOtherReplicaWithItsStamp() {
        replicator("am-1", PEERS).catalogSaved("models", "Airbus A320", new Thing("Airbus A320", 1));

        List<OutboxEntry> entries = sent();
        assertEquals(List.of("am-0", "am-2"), entries.stream().map(OutboxEntry::getTargetNode).toList());
        assertEquals("PUT", entries.get(0).getMethod());
        assertEquals("/internal/replicas/models?key=Airbus%20A320", entries.get(0).getPath());
        ReplicaEnvelope envelope = envelopeOf(entries.get(0));
        assertEquals("Airbus A320", envelope.payload().get("name").asString());
        assertEquals(1_000L, envelope.stamp());
        assertEquals("am-1", envelope.stampNode());
        assertFalse(envelope.deleted());
        assertNull(envelope.home());
    }

    @Test
    void ensureCopiesAreDeliveredRightAwayNotOnlyInTheBackground() {
        replicator("am-1", PEERS).recordSaved("things", "t-1", new Thing("t", 1), 0L);

        List<OutboxEntry> entries = sent();
        verify(dispatcher).deliverNow(entries);
    }

    @Test
    void ensureCopiesOfATransactionAreDeliveredOnlyOnceItIsCommitted() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            replicator("am-1", PEERS).recordSaved("things", "t-1", new Thing("t", 1), 0L);
            verify(dispatcher, never()).deliverNow(any());

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            List<OutboxEntry> entries = sent();
            verify(dispatcher).deliverNow(entries);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void ensureFailureToSendTheCopiesRightAwayDoesNotFailTheCommittedChange() {
        doThrow(new IllegalStateException("outbox busy")).when(dispatcher).deliverNow(any());
        TransactionSynchronizationManager.initSynchronization();
        try {
            replicator("am-1", PEERS).recordSaved("things", "t-1", new Thing("t", 1), 0L);

            assertDoesNotThrow(() -> TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void ensureNewRecordGoesToTheSuccessorAndThisReplicaBecomesItsHome() {
        replicator("am-1", PEERS).recordSaved("things", "t-1", new Thing("t", 1), 0L);

        List<OutboxEntry> entries = sent();
        assertEquals(List.of("am-2"), entries.stream().map(OutboxEntry::getTargetNode).toList());
        assertEquals("am-1", envelopeOf(entries.get(0)).home());
        assertEquals(Optional.of("am-1"), replicator("am-1", PEERS).homeOf("things", "t-1"));
    }

    @Test
    void ensureChangeOnTheSuccessorGoesBackToTheHome() {
        ReplicaState known = new ReplicaState("things", "t-1");
        known.changed("am-0", 500, "am-0", false);
        stored.put(known.getId(), known);

        replicator("am-1", PEERS).recordSaved("things", "t-1", new Thing("t", 2), 3L);

        assertEquals(List.of("am-0"), sent().stream().map(OutboxEntry::getTargetNode).toList());
    }

    @Test
    void ensureDeletionIsSentAsATombstoneAndRemembered() {
        replicator("am-1", PEERS).recordDeleted("things", "t-1");

        OutboxEntry entry = sent().get(0);
        assertEquals("PUT", entry.getMethod());
        assertEquals("am-2", entry.getTargetNode());
        ReplicaEnvelope envelope = envelopeOf(entry);
        assertTrue(envelope.deleted());
        assertTrue(envelope.payload() == null || envelope.payload().isNull());
        assertTrue(stored.get("things:t-1").isDeleted());
    }

    @Test
    void ensureStampIsAlwaysLaterThanTheLastKnownChangeEvenIfTheClockIsBehind() {
        ReplicaState known = new ReplicaState("models", "x");
        known.changed(null, 5_000, "am-2", false);
        stored.put(known.getId(), known);

        replicator("am-1", PEERS).catalogSaved("models", "x", new Thing("x", 2));

        assertEquals(5_001L, envelopeOf(sent().get(0)).stamp());
    }

    @Test
    void ensureAppliedReplicasAreNotSentAgain() {
        Replicator replicator = replicator("am-1", PEERS);

        replicator.asReplica(() -> {
            replicator.catalogSaved("models", "x", new Thing("x", 1));
            replicator.recordSaved("things", "t-1", new Thing("t", 1), 0L);
            return null;
        });

        assertTrue(sent().isEmpty());
        assertFalse(replicator.isApplyingReplica());
    }

    @Test
    void ensureNothingIsSentWithoutPeers() {
        replicator("local", List.of()).catalogSaved("models", "x", new Thing("x", 1));

        assertTrue(sent().isEmpty());
    }
}
