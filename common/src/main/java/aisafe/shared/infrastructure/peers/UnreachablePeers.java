package aisafe.shared.infrastructure.peers;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Collects, for the current request, the peers that did not answer. {@link PartialResultAdvice}
 * turns them into the {@code X-Unreachable-Peers} and {@code X-Partial-Result} response headers.
 */
public final class UnreachablePeers {

    private UnreachablePeers() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** Peers that did not answer while the response was built: the response may be incomplete. */
    static void add(Collection<String> peers) {
        add(peers, true);
    }

    /**
     * Peers that did not answer while the response was built.
     *
     * @param resultMayBeIncomplete false when every piece of data they hold was also obtained from
     *                              another replica (it is copied there), so nothing is missing
     */
    @SuppressWarnings("unchecked")
    public static void add(Collection<String> peers, boolean resultMayBeIncomplete) {
        HttpServletRequest request = ForwardingHeaders.currentRequest();
        if (request == null || peers.isEmpty()) {
            return;
        }
        if (resultMayBeIncomplete) {
            request.setAttribute(PeerHeaders.INCOMPLETE_ATTRIBUTE, Boolean.TRUE);
        }
        Set<String> collected = (Set<String>) request.getAttribute(PeerHeaders.UNREACHABLE_ATTRIBUTE);
        if (collected == null) {
            collected = new LinkedHashSet<>();
            request.setAttribute(PeerHeaders.UNREACHABLE_ATTRIBUTE, collected);
        }
        collected.addAll(peers);
    }

    static boolean mayBeIncomplete(HttpServletRequest request) {
        return Boolean.TRUE.equals(request.getAttribute(PeerHeaders.INCOMPLETE_ATTRIBUTE));
    }

    @SuppressWarnings("unchecked")
    static Set<String> of(HttpServletRequest request) {
        Set<String> collected = (Set<String>) request.getAttribute(PeerHeaders.UNREACHABLE_ATTRIBUTE);
        return collected == null ? Set.of() : collected;
    }
}
