package aisafe.shared.infrastructure.replication;

import aisafe.shared.infrastructure.peers.PeerClient;
import aisafe.shared.infrastructure.security.ServiceTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxDispatcherTest {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final List<String> PEERS = List.of("http://am-0.am:8081", "http://am-1.am:8081", "http://am-2.am:8081");

    private OutboxRepository outbox;
    private PeerClient peerClient;
    private OutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        outbox = mock(OutboxRepository.class);
        peerClient = mock(PeerClient.class);
        dispatcher = new OutboxDispatcher(outbox, new ReplicaRing("am-0", PEERS), peerClient, new ServiceTokens(SECRET, "am-0"));
    }

    private static OutboxEntry entry(long id, String target, String method) {
        OutboxEntry entry = new OutboxEntry(target, method, "/internal/replicas/things?key=" + id, "{}");
        ReflectionTestUtils.setField(entry, "id", id);
        return entry;
    }

    private static PeerClient.PeerResponse answer(int status) {
        return new PeerClient.PeerResponse("peer", HttpStatus.valueOf(status), HttpHeaders.EMPTY, new byte[0], false);
    }

    @Test
    void ensureDeliveredCommandsAreRemovedAndSentWithTheServiceToken() {
        OutboxEntry first = entry(1, "am-1", "PUT");
        when(outbox.findTop200ByOrderByIdAsc()).thenReturn(List.of(first));
        when(peerClient.send(eq("http://am-1.am:8081"), eq(HttpMethod.PUT), any(), any(), any())).thenReturn(answer(204));

        dispatcher.dispatch();

        verify(outbox).delete(first);
        verify(peerClient).send(any(), any(), any(), argThat(h -> h.getFirst(HttpHeaders.AUTHORIZATION).startsWith("Bearer ")), any());
    }

    @Test
    void ensureAFailedCommandIsRetriedLaterAndHoldsBackLaterOnesForTheSamePeer() {
        OutboxEntry first = entry(1, "am-1", "PUT");
        OutboxEntry second = entry(2, "am-1", "PUT");
        OutboxEntry otherPeer = entry(3, "am-2", "PUT");
        when(outbox.findTop200ByOrderByIdAsc()).thenReturn(List.of(first, second, otherPeer));
        when(peerClient.send(eq("http://am-1.am:8081"), any(), any(), any(), any())).thenReturn(PeerClient.PeerResponse.unreachableFor("am-1"));
        when(peerClient.send(eq("http://am-2.am:8081"), any(), any(), any(), any())).thenReturn(answer(204));

        dispatcher.dispatch();

        verify(peerClient, times(1)).send(eq("http://am-1.am:8081"), any(), any(), any(), any());
        verify(outbox).save(first);
        assertEquals(1, first.getAttempts());
        assertTrue(first.getNextAttemptAt().isAfter(Instant.now()));
        verify(outbox, never()).delete(second);
        verify(outbox).delete(otherPeer);
    }

    @Test
    void ensureCommandsDeliveredRightAwayAreRemovedAndFailedOnesKeptForLater() {
        OutboxEntry toAm1 = entry(1, "am-1", "PUT");
        OutboxEntry toAm2 = entry(2, "am-2", "PUT");
        when(peerClient.send(eq("http://am-1.am:8081"), any(), any(), any(), any())).thenReturn(answer(204));
        when(peerClient.send(eq("http://am-2.am:8081"), any(), any(), any(), any())).thenReturn(PeerClient.PeerResponse.unreachableFor("am-2"));

        dispatcher.deliverNow(List.of(toAm1, toAm2));

        verify(outbox).delete(toAm1);
        verify(outbox, never()).delete(toAm2);
        verify(outbox).save(toAm2);
        assertEquals(1, toAm2.getAttempts());
    }

    @Test
    void ensureCommandBeingSentRightAwayIsNotSentAgainByTheBackgroundDispatch() throws Exception {
        OutboxEntry entry = entry(1, "am-1", "PUT");
        CountDownLatch inFlight = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(outbox.findTop200ByOrderByIdAsc()).thenReturn(List.of(entry));
        when(peerClient.send(any(), any(), any(), any(), any())).thenAnswer(i -> {
            inFlight.countDown();
            release.await(5, TimeUnit.SECONDS);
            return answer(204);
        });

        Thread rightAway = Thread.ofVirtual().start(() -> dispatcher.deliverNow(List.of(entry)));
        assertTrue(inFlight.await(5, TimeUnit.SECONDS));
        dispatcher.dispatch();
        release.countDown();
        rightAway.join();

        verify(peerClient, times(1)).send(any(), any(), any(), any(), any());
    }

    @Test
    void ensureCommandIsSetAsideAfterTooManyAttempts() {
        OutboxEntry stuck = entry(1, "am-1", "PUT");
        ReflectionTestUtils.setField(stuck, "attempts", OutboxDispatcher.MAX_ATTEMPTS - 1);
        when(outbox.findTop200ByOrderByIdAsc()).thenReturn(new ArrayList<>(List.of(stuck)));
        when(peerClient.send(any(), any(), any(), any(), any())).thenReturn(answer(409));

        dispatcher.dispatch();

        verify(outbox).delete(stuck);
        verify(outbox, never()).save(stuck);
    }
}
