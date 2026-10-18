package aisafe.security.infrastructure.replication;

import aisafe.security.application.AccountsOnPeers;
import aisafe.security.domain.User;
import aisafe.shared.infrastructure.peers.PeerClient;
import aisafe.shared.infrastructure.security.ServiceTokens;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * {@link AccountsOnPeers} over HTTP: asks every other login in parallel, as this login (the user
 * logging in has no token yet).
 */
@Component
public class PeerAccounts implements AccountsOnPeers {

    private final PeerClient peerClient;
    private final ServiceTokens serviceTokens;
    private final ObjectMapper objectMapper;

    public PeerAccounts(PeerClient peerClient, ServiceTokens serviceTokens, ObjectMapper objectMapper) {
        this.peerClient = peerClient;
        this.serviceTokens = serviceTokens;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        if (!peerClient.hasPeers()) {
            return Optional.empty();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(serviceTokens.current());
        String path = AccountInternalController.PATH + "/" + UriUtils.encodePathSegment(username, StandardCharsets.UTF_8);
        return peerClient.getFirst(path, headers).found()
                .map(response -> objectMapper.readValue(response.body(), UserReplica.class).toDomain());
    }
}
