package aisafe.shared.infrastructure.security;

import aisafe.shared.infrastructure.peers.PeerHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Audit trail: one JSON line per request to the API ({@code /api/**}), in its own log, apart from
 * the others (logger {@code AUDIT}, written to {@code aisafe.audit.file} by logback-spring.xml).
 *
 * <p>Each line says when, who (user and roles, none when the request had no valid token), what
 * (method, path and query), the result (status), the replica that answered, the correlation id,
 * the replica that forwarded the request (if any) and how long it took. It runs before Spring
 * Security, so requests refused there (401, 403) are recorded too. Bodies and headers are never
 * written: they hold passwords and tokens.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AuditFilter extends OncePerRequestFilter {

    /** Request attributes set by {@link JwtAuthenticationFilter} once the token is valid. */
    static final String USER_ATTRIBUTE = "aisafe.audit.user";
    static final String ROLES_ATTRIBUTE = "aisafe.audit.roles";

    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");

    private final String nodeId;
    private final ObjectMapper objectMapper;

    public AuditFilter(@Value("${aisafe.node.id}") String nodeId, ObjectMapper objectMapper) {
        this.nodeId = nodeId;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        boolean failed = true;
        try {
            filterChain.doFilter(request, response);
            failed = false;
        } finally {
            // An exception that escapes here becomes a 500, after this filter
            record(request, response, failed ? 500 : response.getStatus(), System.currentTimeMillis() - start);
        }
    }

    private void record(HttpServletRequest request, HttpServletResponse response, int status, long millis) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("time", Instant.now().toString());
        line.put("user", request.getAttribute(USER_ATTRIBUTE));
        line.put("roles", request.getAttribute(ROLES_ATTRIBUTE) instanceof List<?> roles ? roles : List.of());
        line.put("method", request.getMethod());
        line.put("path", request.getQueryString() == null ? request.getRequestURI()
                : request.getRequestURI() + "?" + request.getQueryString());
        line.put("status", status);
        line.put("node", nodeId);
        line.put("correlationId", response.getHeader(PeerHeaders.CORRELATION_ID));
        line.put("forwardedBy", request.getHeader(PeerHeaders.FORWARDED));
        line.put("ms", millis);
        AUDIT.info(objectMapper.writeValueAsString(line));
    }
}
