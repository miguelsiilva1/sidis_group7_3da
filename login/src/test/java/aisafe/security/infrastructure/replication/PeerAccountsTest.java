package aisafe.security.infrastructure.replication;

import aisafe.security.domain.Role;
import aisafe.security.domain.User;
import aisafe.shared.infrastructure.peers.PeerClient;
import aisafe.shared.infrastructure.security.ServiceTokens;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PeerAccountsTest {

    private final PeerClient peerClient = mock(PeerClient.class);
    private final ServiceTokens serviceTokens = mock(ServiceTokens.class);
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final PeerAccounts accounts = new PeerAccounts(peerClient, serviceTokens, objectMapper);

    @Test
    void ensureAccountFoundOnAnotherLoginIsReturnedAskingAsThisLogin() {
        UserReplica account = new UserReplica(UUID.randomUUID(), "new user", "hash", Role.ATCC);
        when(peerClient.hasPeers()).thenReturn(true);
        when(serviceTokens.current()).thenReturn("service-token");
        when(peerClient.getFirst(eq("/internal/accounts/new%20user"), any())).thenReturn(new PeerClient.PeerLookup(
                Optional.of(new PeerClient.PeerResponse("http://login-1", HttpStatus.OK, new HttpHeaders(),
                        objectMapper.writeValueAsBytes(account), false)), List.of()));

        User found = accounts.findByUsername("new user").orElseThrow();

        assertEquals(account.userID(), found.getUserID());
        assertEquals("hash", found.getPassword());
        ArgumentCaptor<HttpHeaders> headers = ArgumentCaptor.forClass(HttpHeaders.class);
        verify(peerClient).getFirst(any(), headers.capture());
        assertEquals("Bearer service-token", headers.getValue().getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void ensureAccountOnNoLoginIsEmpty() {
        when(peerClient.hasPeers()).thenReturn(true);
        when(peerClient.getFirst(any(), any())).thenReturn(new PeerClient.PeerLookup(Optional.empty(), List.of()));

        assertTrue(accounts.findByUsername("nobody").isEmpty());
    }

    @Test
    void ensureLoginWithoutPeersAsksNobody() {
        assertTrue(accounts.findByUsername("nobody").isEmpty());
        verify(peerClient, never()).getFirst(any(), any());
    }
}
