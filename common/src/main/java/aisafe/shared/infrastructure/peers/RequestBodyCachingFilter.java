package aisafe.shared.infrastructure.peers;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.util.Set;

/**
 * Keeps the body of updates and deletes (PUT, PATCH, DELETE) once it has been read, so
 * {@link PeerForwardingAdvice} can send the same request to the replica that holds the record.
 * Multipart uploads are not kept (and so not forwarded).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestBodyCachingFilter extends OncePerRequestFilter {

    static final Set<String> FORWARDABLE_WRITES = Set.of("PUT", "PATCH", "DELETE");
    private static final int MAX_CACHED_BYTES = 1_000_000;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String contentType = request.getContentType();
        boolean multipart = contentType != null && contentType.startsWith(MediaType.MULTIPART_FORM_DATA_VALUE);
        if (FORWARDABLE_WRITES.contains(request.getMethod()) && !multipart) {
            filterChain.doFilter(new ContentCachingRequestWrapper(request, MAX_CACHED_BYTES), response);
        } else {
            filterChain.doFilter(request, response);
        }
    }
}
