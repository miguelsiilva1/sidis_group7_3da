package aisafe.shared.infrastructure.replication;

import aisafe.shared.infrastructure.peers.PartialResultAdvice;
import aisafe.shared.infrastructure.peers.PeerClient;
import aisafe.shared.infrastructure.peers.PeerHeaders;
import aisafe.shared.infrastructure.security.ServiceTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReplicaGathererTest {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final String AM0 = "http://am-0.am:8081";
    private static final String AM1 = "http://am-1.am:8081";
    private static final String AM2 = "http://am-2.am:8081";

    record Thing(String name, int size) {}

    private final PeerClient peerClient = mock(PeerClient.class);
    private MockHttpServletRequest request;

    @SuppressWarnings("unchecked")
    private final ReplicaHandler<Thing> handler = mock(ReplicaHandler.class);
    private final ReplicaExporter exporter = mock(ReplicaExporter.class);
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("GET", "/api/things/report");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer user-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(handler.type()).thenReturn("things");
        when(handler.payloadType()).thenReturn(Thing.class);
        when(handler.isCatalog()).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private ReplicaGatherer gatherer() {
        return new ReplicaGatherer(new ReplicaRing("am-0", List.of(AM0, AM1, AM2)), peerClient,
                new ServiceTokens(SECRET, "am-0"), exporter, json);
    }

    private static PeerClient.PeerResponse answer(String peer, String json) {
        return new PeerClient.PeerResponse(peer, HttpStatus.OK, HttpHeaders.EMPTY, json.getBytes(StandardCharsets.UTF_8), false);
    }

    private ReplicaEnvelope local(String key, long version, int size, long stamp) {
        return new ReplicaEnvelope(key, "am-0", version, json.valueToTree(new Thing(key, size)), stamp, "am-0", false);
    }

    private static String envelope(String key, long version, int size, long stamp) {
        return "{\"key\":\"" + key + "\",\"home\":\"am-1\",\"version\":" + version
                + ",\"payload\":{\"name\":\"" + key + "\",\"size\":" + size + "},\"stamp\":" + stamp
                + ",\"stampNode\":\"am-1\",\"deleted\":false}";
    }

    private static String tombstone(String key, long stamp) {
        return "{\"key\":\"" + key + "\",\"home\":\"am-1\",\"stamp\":" + stamp + ",\"stampNode\":\"am-1\",\"deleted\":true}";
    }

    /** The headers the response would get. */
    private HttpHeaders responseHeaders() {
        ServletServerHttpResponse response = new ServletServerHttpResponse(new MockHttpServletResponse());
        new PartialResultAdvice().beforeBodyWrite(null, null, null, null, new ServletServerHttpRequest(request), response);
        return response.getHeaders();
    }

    @Test
    void ensureEntitiesOfEveryReplicaAreJoinedOncePerKeyKeepingTheLatestChange() {
        when(exporter.export(handler, null)).thenReturn(List.of(local("b", 1, 1, 100)));
        when(peerClient.getFromAll(eq("/internal/replicas/things"), any())).thenReturn(List.of(
                answer(AM1, "[" + envelope("b", 3, 30, 300) + "," + envelope("c", 0, 5, 50) + "]"),
                answer(AM2, "[" + envelope("a", 2, 7, 70) + "," + envelope("b", 2, 20, 200) + "]")));

        List<ReplicaHandler.Item<Thing>> items = gatherer().gather(handler);

        assertEquals(List.of("a", "b", "c"), items.stream().map(ReplicaHandler.Item::key).toList());
        assertEquals(new Thing("b", 30), items.get(1).payload());
        assertEquals(3L, items.get(1).version());
        assertNull(responseHeaders().getFirst(PeerHeaders.UNREACHABLE_PEERS));
    }

    @Test
    void ensureDeletedEntitiesAreLeftOutEvenIfAStaleCopyStillHasThem() {
        when(exporter.export(handler, null)).thenReturn(List.of(
                new ReplicaEnvelope("gone", "am-0", null, null, 500L, "am-0", true)));
        when(peerClient.getFromAll(any(), any())).thenReturn(List.of(
                answer(AM1, "[" + envelope("gone", 4, 1, 400) + "," + envelope("kept", 1, 1, 100) + "]"),
                answer(AM2, "[" + envelope("kept", 1, 1, 100) + "," + tombstone("later", 900) + "," + envelope("later", 1, 1, 800) + "]")));

        assertEquals(List.of("kept"), gatherer().gather(handler).stream().map(ReplicaHandler.Item::key).toList());
    }

    @Test
    void ensurePeersAreAskedWithTheServiceTokenNotTheUsersToken() {
        when(exporter.export(handler, null)).thenReturn(List.of());
        when(peerClient.getFromAll(any(), any())).thenReturn(List.of(answer(AM1, "[]"), answer(AM2, "[]")));

        gatherer().gather(handler);

        ArgumentCaptor<HttpHeaders> headers = ArgumentCaptor.forClass(HttpHeaders.class);
        verify(peerClient).getFromAll(eq("/internal/replicas/things"), headers.capture());
        String authorization = headers.getValue().getFirst(HttpHeaders.AUTHORIZATION);
        assertTrue(authorization.startsWith("Bearer ") && !authorization.equals("Bearer user-token"));
    }

    @Test
    void ensureOnePeerDownIsReportedButTheResultIsNotPartialBecauseEveryRecordHasACopy() {
        when(exporter.export(handler, null)).thenReturn(List.of());
        when(peerClient.getFromAll(any(), any())).thenReturn(List.of(
                answer(AM1, "[" + envelope("a", 0, 1, 10) + "]"),
                PeerClient.PeerResponse.unreachableFor(AM2)));

        gatherer().gather(handler);

        HttpHeaders headers = responseHeaders();
        assertEquals(AM2, headers.getFirst(PeerHeaders.UNREACHABLE_PEERS));
        assertNull(headers.getFirst(PeerHeaders.PARTIAL_RESULT));
    }

    @Test
    void ensureBothHoldersOfSomeRecordsDownMakesTheResultPartial() {
        when(exporter.export(handler, null)).thenReturn(List.of());
        when(peerClient.getFromAll(any(), any())).thenReturn(List.of(
                PeerClient.PeerResponse.unreachableFor(AM1),
                new PeerClient.PeerResponse(AM2, HttpStatus.UNAUTHORIZED, HttpHeaders.EMPTY, new byte[0], false)));

        gatherer().gather(handler);

        HttpHeaders headers = responseHeaders();
        assertEquals(AM1 + "," + AM2, headers.getFirst(PeerHeaders.UNREACHABLE_PEERS));
        assertEquals("true", headers.getFirst(PeerHeaders.PARTIAL_RESULT));
    }

    @Test
    void ensureCatalogsAreNeverPartialSinceEveryReplicaHasThem() {
        when(handler.isCatalog()).thenReturn(true);
        when(exporter.export(handler, null)).thenReturn(List.of());
        when(peerClient.getFromAll(any(), any())).thenReturn(List.of(
                PeerClient.PeerResponse.unreachableFor(AM1), PeerClient.PeerResponse.unreachableFor(AM2)));

        gatherer().gather(handler);

        assertNull(responseHeaders().getFirst(PeerHeaders.PARTIAL_RESULT));
    }

    @Test
    void ensureOnlyLocalDataWithoutPeers() {
        when(exporter.export(handler, null)).thenReturn(List.of(local("a", 0, 1, 10)));
        ReplicaGatherer alone = new ReplicaGatherer(new ReplicaRing("local", List.of()), peerClient,
                new ServiceTokens(SECRET, "local"), exporter, json);

        assertEquals(1, alone.gather(handler).size());
        verifyNoInteractions(peerClient);
    }

    @Test
    void ensureMissingRecordsNeedBothHoldersDown() {
        ReplicaGatherer gatherer = gatherer();

        assertFalse(gatherer.someRecordsMissing(List.of(AM1)));
        assertFalse(gatherer.someRecordsMissing(List.of(AM2)));
        assertTrue(gatherer.someRecordsMissing(List.of(AM1, AM2)));
    }
}
