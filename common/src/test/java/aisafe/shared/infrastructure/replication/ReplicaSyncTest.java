package aisafe.shared.infrastructure.replication;

import aisafe.shared.infrastructure.peers.PeerClient;
import aisafe.shared.infrastructure.security.ServiceTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReplicaSyncTest {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final String AM1 = "http://am-1.am:8081";
    private static final String AM2 = "http://am-2.am:8081";

    record Thing(String name) {}

    private final PeerClient peerClient = mock(PeerClient.class);
    private final ReplicaApplier applier = mock(ReplicaApplier.class);
    @SuppressWarnings("unchecked")
    private final ReplicaHandler<Thing> catalog = mock(ReplicaHandler.class);
    @SuppressWarnings("unchecked")
    private final ReplicaHandler<Thing> records = mock(ReplicaHandler.class);
    private ReplicaSync sync;

    private static PeerClient.PeerResponse answer(String peer, String json) {
        return new PeerClient.PeerResponse(peer, HttpStatus.OK, HttpHeaders.EMPTY, json.getBytes(StandardCharsets.UTF_8), false);
    }

    private static String change(String key) {
        return "{\"key\":\"" + key + "\",\"home\":\"am-1\",\"payload\":{\"name\":\"" + key + "\"},\"stamp\":10,\"stampNode\":\"am-1\",\"deleted\":false}";
    }

    @BeforeEach
    void setUp() {
        when(catalog.type()).thenReturn("models");
        when(catalog.order()).thenReturn(10);
        when(records.type()).thenReturn("things");
        when(records.order()).thenReturn(50);
        sync = new ReplicaSync(List.of(records, catalog), new ReplicaRing("am-0", List.of("http://am-0.am:8081", AM1, AM2)),
                peerClient, new ServiceTokens(SECRET, "am-0"), applier, JsonMapper.builder().build());
        when(peerClient.send(any(), eq(HttpMethod.GET), any(), any(), any())).thenReturn(answer("peer", "[]"));
    }

    @Test
    void ensureStartupAsksEveryPeerForWhatThisReplicaShouldHoldCatalogsFirst() throws Exception {
        when(peerClient.send(eq(AM1), eq(HttpMethod.GET), eq("/internal/replicas/things?for=am-0"), any(), any()))
                .thenReturn(answer(AM1, "[" + change("t-1") + "]"));

        sync.run(null);

        var order = inOrder(peerClient);
        order.verify(peerClient).send(eq(AM1), eq(HttpMethod.GET), eq("/internal/replicas/models?for=am-0"), any(), any());
        order.verify(peerClient).send(eq(AM1), eq(HttpMethod.GET), eq("/internal/replicas/things?for=am-0"), any(), any());
        verify(applier).apply(eq(records), argThat(e -> e.key().equals("t-1")));
    }

    @Test
    void ensurePeriodicSyncWaitsForTheStartupRound() throws Exception {
        sync.periodicSync();
        verifyNoInteractions(peerClient);

        sync.run(null);
        clearInvocations(peerClient);
        sync.periodicSync();

        verify(peerClient, times(4)).send(any(), eq(HttpMethod.GET), any(), any(), any());
    }

    @Test
    void ensurePeerThatDoesNotAnswerIsSkippedAndTheOthersStillSynced() throws Exception {
        when(peerClient.send(eq(AM1), any(), any(), any(), any())).thenReturn(PeerClient.PeerResponse.unreachableFor(AM1));
        when(peerClient.send(eq(AM2), eq(HttpMethod.GET), eq("/internal/replicas/things?for=am-0"), any(), any()))
                .thenReturn(answer(AM2, "[" + change("t-2") + "]"));

        sync.run(null);

        verify(applier).apply(eq(records), argThat(e -> e.key().equals("t-2")));
    }

    @Test
    void ensureAnswerFromAnOlderReplicaIsReadAndAnUnreadableOneIsSkipped() throws Exception {
        String olderFormat = "{\"key\":\"t-old\",\"home\":\"am-1\",\"version\":1,\"payload\":{\"name\":\"t-old\"}}";
        when(peerClient.send(eq(AM1), eq(HttpMethod.GET), eq("/internal/replicas/things?for=am-0"), any(), any()))
                .thenReturn(answer(AM1, "[" + olderFormat + "]"));
        when(peerClient.send(eq(AM2), eq(HttpMethod.GET), eq("/internal/replicas/things?for=am-0"), any(), any()))
                .thenReturn(answer(AM2, "not json"));

        sync.run(null);

        verify(applier).apply(eq(records), argThat(e -> e.key().equals("t-old") && !e.deleted() && e.stamp() == null));
    }
}
