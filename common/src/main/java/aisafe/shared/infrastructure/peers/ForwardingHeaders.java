package aisafe.shared.infrastructure.peers;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Builds the headers a replica sends when it forwards the current client request to a peer.
 *
 * <ul>
 *   <li>the user's token, so the peer applies the same access rules;</li>
 *   <li>the correlation id, so one request can be followed across replicas in the logs;</li>
 *   <li>the original host, port and scheme ({@code X-Forwarded-*}), so the links the peer puts in
 *   its response point at the address the client used, not at the peer's internal address.</li>
 * </ul>
 */
public final class ForwardingHeaders {

    private ForwardingHeaders() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static HttpHeaders fromCurrentRequest() {
        HttpHeaders headers = new HttpHeaders();
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return headers;
        }
        copy(request, headers, HttpHeaders.AUTHORIZATION);
        String correlationId = MDC.get(RequestTracingFilter.MDC_CORRELATION_ID);
        if (correlationId != null) {
            headers.set(PeerHeaders.CORRELATION_ID, correlationId);
        }
        headers.set("X-Forwarded-Host", request.getServerName());
        headers.set("X-Forwarded-Port", String.valueOf(request.getServerPort()));
        headers.set("X-Forwarded-Proto", request.getScheme());
        return headers;
    }

    static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    static boolean isForwardedRequest() {
        HttpServletRequest request = currentRequest();
        return request != null && request.getHeader(PeerHeaders.FORWARDED) != null;
    }

    private static void copy(HttpServletRequest request, HttpHeaders headers, String name) {
        String value = request.getHeader(name);
        if (value != null) {
            headers.set(name, value);
        }
    }
}
