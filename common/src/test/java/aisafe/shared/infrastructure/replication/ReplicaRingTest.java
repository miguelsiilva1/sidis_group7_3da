package aisafe.shared.infrastructure.replication;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ReplicaRingTest {

    private static final List<String> PEERS = List.of(
            "http://am-2.am.ns.svc:8081", "http://am-0.am.ns.svc:8081", "http://am-1.am.ns.svc:8081");

    @Test
    void ensureMembersAreOrderedAndSuccessorsWrapAround() {
        ReplicaRing ring = new ReplicaRing("am-1", PEERS);

        assertTrue(ring.isEnabled());
        assertEquals(Optional.of("am-1"), ring.successorOf("am-0"));
        assertEquals(Optional.of("am-2"), ring.successorOf("am-1"));
        assertEquals(Optional.of("am-0"), ring.successorOf("am-2"));
        assertEquals(List.of("am-0", "am-2"), ring.others().stream().map(ReplicaRing.Member::nodeId).toList());
        assertEquals("http://am-2.am.ns.svc:8081", ring.member("am-2").orElseThrow().url());
    }

    @Test
    void ensureRecordIsHeldByItsHomeAndTheNextReplica() {
        ReplicaRing ring = new ReplicaRing("am-0", PEERS);

        assertEquals(List.of("am-2", "am-0"), ring.holdersOf("am-2"));
        assertEquals(List.of("am-0", "am-1"), ring.holdersOf("am-0"));
    }

    @Test
    void ensureRingIsDisabledWithoutPeersOrWhenThisReplicaIsNotInIt() {
        assertFalse(new ReplicaRing("local", List.of()).isEnabled());
        assertFalse(new ReplicaRing("local", PEERS).isEnabled());
        assertFalse(new ReplicaRing("am-0", List.of("http://am-0.am:8081")).isEnabled());
    }

    @Test
    void ensureNodeIdIsTheFirstLabelOfTheHostName() {
        assertEquals("am-1", ReplicaRing.nodeIdOf("http://am-1.am.ns.svc.cluster.local:8081"));
        assertEquals("localhost", ReplicaRing.nodeIdOf("http://localhost:8081"));
    }
}
