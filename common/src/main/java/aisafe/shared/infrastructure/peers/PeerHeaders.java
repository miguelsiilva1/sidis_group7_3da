package aisafe.shared.infrastructure.peers;

/**
 * HTTP headers used between replicas and shown to clients.
 */
public final class PeerHeaders {
    /** Set on requests forwarded by a peer, with the forwarding replica's id. Never forwarded again. */
    public static final String FORWARDED = "X-Peer-Forwarded";
    /** Set on every response: the replica that answered the client. */
    public static final String NODE_ID = "X-Node-Id";
    /** Set when the data came from a peer: the replica that holds it. */
    public static final String DATA_NODE = "X-Data-Node";
    /** Set to true when some peers did not answer and the response may be incomplete because of it. */
    public static final String PARTIAL_RESULT = "X-Partial-Result";
    /** The peers that did not answer. Data copied on another replica may still make the response complete. */
    public static final String UNREACHABLE_PEERS = "X-Unreachable-Peers";
    /** Identifies one client request across services and replicas. */
    public static final String CORRELATION_ID = "X-Correlation-Id";

    /** Request attribute where the unreachable peers of the current request are collected. */
    static final String UNREACHABLE_ATTRIBUTE = "aisafe.peers.unreachable";
    /** Request attribute set when the response may be incomplete because of the unreachable peers. */
    static final String INCOMPLETE_ATTRIBUTE = "aisafe.peers.incomplete";

    private PeerHeaders() {
        throw new UnsupportedOperationException("Utility class");
    }
}
