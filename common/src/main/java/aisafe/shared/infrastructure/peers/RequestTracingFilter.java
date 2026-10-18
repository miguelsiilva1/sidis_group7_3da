package aisafe.shared.infrastructure.peers;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Tags every request and response with the replica and the correlation id.
 *
 * <ul>
 *   <li>{@code X-Node-Id}: the replica that answered;</li>
 *   <li>{@code X-Correlation-Id}: taken from the request or created here, put in the logging
 *   context (MDC) and sent on to peers and other services, so one client request can be
 *   followed through every log.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTracingFilter extends OncePerRequestFilter {

    static final String MDC_CORRELATION_ID = "correlationId";
    static final String MDC_NODE_ID = "nodeId";

    private final String nodeId;

    public RequestTracingFilter(@Value("${aisafe.node.id}") String nodeId) {
        this.nodeId = nodeId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(PeerHeaders.CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_CORRELATION_ID, correlationId);
        MDC.put(MDC_NODE_ID, nodeId);
        response.setHeader(PeerHeaders.NODE_ID, nodeId);
        response.setHeader(PeerHeaders.CORRELATION_ID, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
            MDC.remove(MDC_NODE_ID);
        }
    }
}
