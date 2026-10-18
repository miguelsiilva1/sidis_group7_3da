package aisafe.shared.infrastructure.peers;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Tells the {@link PeerClient} when a peer sends a request, so a peer that is back (it asks its
 * peers for its data on startup, and again every few seconds) is asked again at once.
 */
@Component
public class PeerContactFilter extends OncePerRequestFilter {

    private final PeerClient peerClient;

    public PeerContactFilter(PeerClient peerClient) {
        this.peerClient = peerClient;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String peerNodeId = request.getHeader(PeerHeaders.FORWARDED);
        if (peerNodeId != null && !peerNodeId.isBlank()) {
            peerClient.heardFrom(peerNodeId);
        }
        filterChain.doFilter(request, response);
    }
}
