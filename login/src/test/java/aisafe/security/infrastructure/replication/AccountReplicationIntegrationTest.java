package aisafe.security.infrastructure.replication;

import aisafe.security.domain.Role;
import aisafe.security.domain.User;
import aisafe.security.domain.UserRepository;
import aisafe.shared.infrastructure.replication.OutboxEntry;
import aisafe.shared.infrastructure.replication.OutboxRepository;
import aisafe.shared.infrastructure.replication.ReplicaApplier;
import aisafe.shared.infrastructure.replication.ReplicaEnvelope;
import aisafe.shared.infrastructure.replication.ReplicaExporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This login is login-0 of three (the peers do not exist: nothing is delivered, the test only
 * looks at what is written to the outbox).
 */
@SpringBootTest(properties = {
        "aisafe.node.id=login-0",
        "aisafe.peers=http://login-0.test.invalid:1,http://login-1.test.invalid:1,http://login-2.test.invalid:1",
        "aisafe.replication.dispatch-interval-ms=3600000"
})
@ActiveProfiles("jpa")
@Transactional
class AccountReplicationIntegrationTest {

    @Autowired private OutboxRepository outbox;
    @Autowired private UserRepository userRepository;
    @Autowired private ReplicaApplier applier;
    @Autowired private UserReplicaHandler handler;
    @Autowired private ReplicaExporter exporter;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AccountInternalController controller;

    @BeforeEach
    void setUp() {
        outbox.deleteAll();
    }

    private ReplicaEnvelope fromLogin2(UserReplica account, long stamp) {
        return new ReplicaEnvelope(account.username(), null, null, objectMapper.valueToTree(account), stamp, "login-2", false);
    }

    @Test
    void ensureNewAccountIsSentToEveryOtherLogin() {
        userRepository.save(new User("new-account", "hash-1", Role.ATCC));

        assertEquals(List.of("login-1", "login-2"), outbox.findAll().stream()
                .filter(e -> e.getPath().startsWith("/internal/replicas/accounts"))
                .map(OutboxEntry::getTargetNode)
                .sorted()
                .toList());
    }

    @Test
    void ensureAccountCreatedOnAnotherLoginIsStoredAndNotSentOn() {
        UserReplica account = new UserReplica(UUID.randomUUID(), "from-login-2", "hash-2", Role.MAINTENANCE_TECHNICIAN);

        assertTrue(applier.apply(handler, fromLogin2(account, 1_000)));

        User stored = userRepository.findByUsername("from-login-2").orElseThrow();
        assertEquals(account.userID(), stored.getUserID());
        assertEquals("hash-2", stored.getPassword());
        assertEquals(Role.MAINTENANCE_TECHNICIAN, stored.getRole());
        assertTrue(outbox.findAll().isEmpty(), "a received account is not sent on");
    }

    @Test
    void ensureNewerCopyOfTheSameAccountReplacesTheStoredOne() {
        UserReplica account = new UserReplica(UUID.randomUUID(), "changed", "old-hash", Role.ATCC);
        applier.apply(handler, fromLogin2(account, 1_000));

        applier.apply(handler, fromLogin2(new UserReplica(account.userID(), "changed", "new-hash", Role.BACKOFFICE_OPERATOR), 2_000));

        User stored = userRepository.findByUsername("changed").orElseThrow();
        assertEquals("new-hash", stored.getPassword());
        assertEquals(Role.BACKOFFICE_OPERATOR, stored.getRole());
    }

    @Test
    void ensureSameUsernameCreatedOnTwoLoginsKeepsTheNewestAccount() {
        userRepository.save(new User("twice", "local-hash", Role.ATCC));
        UserReplica newer = new UserReplica(UUID.randomUUID(), "twice", "remote-hash", Role.ATCC);

        applier.apply(handler, fromLogin2(newer, Long.MAX_VALUE));

        User stored = userRepository.findByUsername("twice").orElseThrow();
        assertEquals(newer.userID(), stored.getUserID());
        assertEquals("remote-hash", stored.getPassword());
    }

    @Test
    void ensureAnotherLoginCanAskForOneAccount() {
        userRepository.save(new User("asked", "hash-4", Role.ATCC));

        assertEquals("hash-4", controller.account("asked").getBody().passwordHash());
        assertEquals(404, controller.account("unknown").getStatusCode().value());
    }

    @Test
    void ensureAccountsAreExportedForALoginThatIsRecovering() {
        userRepository.save(new User("exported", "hash-3", Role.ATCC));

        List<ReplicaEnvelope> exported = exporter.export(handler, "login-1");

        assertTrue(exported.stream().anyMatch(e -> e.key().equals("exported") && e.stamp() != null));
    }
}
