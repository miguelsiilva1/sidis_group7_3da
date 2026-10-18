package aisafe.shared.infrastructure.peers;

import aisafe.shared.domain.NotFoundException;
import aisafe.shared.infrastructure.GlobalExceptionHandler.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.WebUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Peer-to-peer forwarding of GET requests for data this replica does not hold.
 *
 * <p>When a GET fails locally with a {@link NotFoundException}, the same request (path, query,
 * the user's token) is sent to every peer in parallel and the first peer that has the data
 * answers the client, with {@code X-Data-Node} naming that peer. If no peer has it, the answer
 * is 404. If some peers could not be asked, the data may be on them: the answer is 503 with
 * {@code Retry-After}, never a 404 for data that may exist. A request that was already forwarded
 * by a peer is never forwarded again.</p>
 *
 * <p>Updates and deletes (PUT, PATCH, DELETE) of a record this replica does not hold are sent,
 * with the same body and headers, to the peers one at a time until one holds the record. A
 * create (POST) is never forwarded: new records stay on the replica that received them.</p>
 *
 * <p>Every service gets this for all its routes without changing its controllers.</p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PeerForwardingAdvice {

    private final PeerClient peerClient;

    public PeerForwardingAdvice(PeerClient peerClient) {
        this.peerClient = peerClient;
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<?> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        boolean notForwardedYet = request.getHeader(PeerHeaders.FORWARDED) == null && peerClient.hasPeers();
        String path = request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        if (notForwardedYet && HttpMethod.GET.matches(request.getMethod())) {
            PeerClient.PeerLookup lookup = peerClient.getFirst(path, ForwardingHeaders.fromCurrentRequest());
            return lookup.found()
                    .<ResponseEntity<?>>map(PeerForwardingAdvice::fromPeer)
                    .orElseGet(() -> notFound(ex, lookup.unreachablePeers()));
        }
        ContentCachingRequestWrapper cached = WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);
        if (notForwardedYet && RequestBodyCachingFilter.FORWARDABLE_WRITES.contains(request.getMethod()) && cached != null) {
            return forwardWrite(ex, request, path, cached.getContentAsByteArray());
        }
        return notFound(ex, List.of());
    }

    private ResponseEntity<?> forwardWrite(NotFoundException ex, HttpServletRequest request, String path, byte[] body) {
        HttpHeaders headers = ForwardingHeaders.fromCurrentRequest();
        copy(request, headers, HttpHeaders.CONTENT_TYPE);
        copy(request, headers, HttpHeaders.IF_MATCH);
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        List<String> unreachable = new ArrayList<>();
        for (String peer : peerClient.peers()) {
            PeerClient.PeerResponse response = peerClient.forwardWrite(peer, method, path, headers, body);
            if (response.unreachable()) {
                unreachable.add(peer);
            } else if (response.status().value() != 404) {
                return fromPeer(response);
            }
        }
        return notFound(ex, unreachable);
    }

    private static void copy(HttpServletRequest request, HttpHeaders headers, String name) {
        String value = request.getHeader(name);
        if (value != null) {
            headers.set(name, value);
        }
    }

    private static ResponseEntity<?> fromPeer(PeerClient.PeerResponse found) {
        HttpHeaders headers = new HttpHeaders();
        if (found.headers().getContentType() != null) {
            headers.setContentType(found.headers().getContentType());
        }
        if (found.headers().getETag() != null) {
            headers.setETag(found.headers().getETag());
        }
        String dataNode = found.headers().getFirst(PeerHeaders.NODE_ID);
        headers.set(PeerHeaders.DATA_NODE, dataNode != null ? dataNode : found.peer());
        return new ResponseEntity<>(found.body(), headers, found.status());
    }

    private static ResponseEntity<ErrorResponse> notFound(NotFoundException ex, List<String> unreachable) {
        if (unreachable.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .header(PeerHeaders.PARTIAL_RESULT, "true")
                .header(PeerHeaders.UNREACHABLE_PEERS, String.join(",", unreachable))
                .body(new ErrorResponse(ex.getMessage() + " on the replicas that answered; some replicas did not answer, try again"));
    }
}
