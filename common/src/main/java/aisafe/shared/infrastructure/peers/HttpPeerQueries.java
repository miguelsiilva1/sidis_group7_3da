package aisafe.shared.infrastructure.peers;

import aisafe.shared.application.PeerQueries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@link PeerQueries} over HTTP, using {@link PeerClient}.
 */
@Component
public class HttpPeerQueries implements PeerQueries {

    private static final Logger log = LoggerFactory.getLogger(HttpPeerQueries.class);

    private final PeerClient peerClient;
    private final ObjectMapper objectMapper;

    public HttpPeerQueries(PeerClient peerClient, ObjectMapper objectMapper) {
        this.peerClient = peerClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> List<T> fetchFromAll(String path, Class<T> elementType) {
        if (!peerClient.hasPeers() || ForwardingHeaders.isForwardedRequest()) {
            return List.of();
        }
        JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
        List<T> all = new ArrayList<>();
        List<String> unreachable = new ArrayList<>();
        for (PeerClient.PeerResponse response : peerClient.getFromAll(path, ForwardingHeaders.fromCurrentRequest())) {
            if (response.found()) {
                List<T> items = objectMapper.readValue(response.body(), listType);
                all.addAll(items);
            } else if (response.unreachable()) {
                unreachable.add(response.peer());
            } else {
                log.warn("Peer {} answered {} to GET {}", response.peer(), response.status().value(), path);
                unreachable.add(response.peer());
            }
        }
        UnreachablePeers.add(unreachable);
        return all;
    }

    @Override
    public <T> Optional<T> fetchFirst(String path, Class<T> type) {
        if (!peerClient.hasPeers() || ForwardingHeaders.isForwardedRequest()) {
            return Optional.empty();
        }
        PeerClient.PeerLookup lookup = peerClient.getFirst(path, ForwardingHeaders.fromCurrentRequest());
        UnreachablePeers.add(lookup.unreachablePeers());
        return lookup.found().map(response -> objectMapper.readValue(response.body(), type));
    }
}
