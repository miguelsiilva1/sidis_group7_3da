package aisafe.shared.infrastructure.peers;

import aisafe.shared.domain.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PeerForwardingAdviceTest {

    private final PeerClient peerClient = mock(PeerClient.class);
    private final PeerForwardingAdvice advice = new PeerForwardingAdvice(peerClient);

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static MockHttpServletRequest request(String method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/things/x");
        request.setQueryString("full=true");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }

    private static PeerClient.PeerResponse found(String peer, String nodeId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setETag("\"3\"");
        headers.set(PeerHeaders.NODE_ID, nodeId);
        return new PeerClient.PeerResponse(peer, HttpStatus.OK, headers, "{\"id\":\"x\"}".getBytes(StandardCharsets.UTF_8), false);
    }

    @Test
    void ensureGetNotFoundLocallyIsAnsweredByThePeerThatHasIt() {
        when(peerClient.hasPeers()).thenReturn(true);
        when(peerClient.getFirst(eq("/api/things/x?full=true"), any()))
                .thenReturn(new PeerClient.PeerLookup(Optional.of(found("http://peer-2", "node-2")), List.of()));

        ResponseEntity<?> response = advice.handleNotFound(new NotFoundException("not here"), request("GET"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("node-2", response.getHeaders().getFirst(PeerHeaders.DATA_NODE));
        assertEquals("\"3\"", response.getHeaders().getETag());
        assertEquals("{\"id\":\"x\"}", new String((byte[]) response.getBody(), StandardCharsets.UTF_8));
    }

    @Test
    void ensureNotFoundOnEveryPeerIs404() {
        when(peerClient.hasPeers()).thenReturn(true);
        when(peerClient.getFirst(any(), any())).thenReturn(new PeerClient.PeerLookup(Optional.empty(), List.of()));

        ResponseEntity<?> response = advice.handleNotFound(new NotFoundException("not here"), request("GET"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getHeaders().getFirst(PeerHeaders.PARTIAL_RESULT));
    }

    @Test
    void ensureNotFoundWithUnreachablePeersIsUnavailableInsteadOf404() {
        when(peerClient.hasPeers()).thenReturn(true);
        when(peerClient.getFirst(any(), any())).thenReturn(new PeerClient.PeerLookup(Optional.empty(), List.of("http://peer-3")));

        ResponseEntity<?> response = advice.handleNotFound(new NotFoundException("not here"), request("GET"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode(), "the data may be on the peer that did not answer");
        assertEquals("1", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertEquals("true", response.getHeaders().getFirst(PeerHeaders.PARTIAL_RESULT));
        assertEquals("http://peer-3", response.getHeaders().getFirst(PeerHeaders.UNREACHABLE_PEERS));
    }

    @Test
    void ensureWriteNotFoundWithUnreachablePeersIsUnavailableInsteadOf404() {
        when(peerClient.hasPeers()).thenReturn(true);
        when(peerClient.peers()).thenReturn(List.of("http://peer-1", "http://peer-2"));
        when(peerClient.forwardWrite(eq("http://peer-1"), any(), any(), any(), any()))
                .thenReturn(PeerClient.PeerResponse.unreachableFor("http://peer-1"));
        when(peerClient.forwardWrite(eq("http://peer-2"), any(), any(), any(), any()))
                .thenReturn(new PeerClient.PeerResponse("http://peer-2", HttpStatus.NOT_FOUND, new HttpHeaders(), new byte[0], false));
        MockHttpServletRequest request = request("DELETE");

        ResponseEntity<?> response = advice.handleNotFound(new NotFoundException("not here"),
                new ContentCachingRequestWrapper(request, 0));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("http://peer-1", response.getHeaders().getFirst(PeerHeaders.UNREACHABLE_PEERS));
    }

    @Test
    void ensureForwardedRequestIsNeverForwardedAgain() {
        when(peerClient.hasPeers()).thenReturn(true);
        MockHttpServletRequest request = request("GET");
        request.addHeader(PeerHeaders.FORWARDED, "node-1");

        ResponseEntity<?> response = advice.handleNotFound(new NotFoundException("not here"), request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(peerClient, never()).getFirst(any(), any());
    }

    @Test
    void ensureOnlyGetRequestsAreForwarded() {
        when(peerClient.hasPeers()).thenReturn(true);

        ResponseEntity<?> response = advice.handleNotFound(new NotFoundException("not here"), request("PATCH"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(peerClient, never()).getFirst(any(), any());
    }
}
