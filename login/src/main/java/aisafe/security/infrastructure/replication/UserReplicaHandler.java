package aisafe.security.infrastructure.replication;

import aisafe.security.domain.UserRepository;
import aisafe.shared.infrastructure.replication.ReplicaHandler;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Applies account replicas. Accounts are a catalog: every login holds all of them, so any login
 * can check any user's credentials.
 */
@Component
public class UserReplicaHandler implements ReplicaHandler<UserReplica> {

    public static final String TYPE = "accounts";

    private final UserRepository userRepository;

    public UserReplicaHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override public String type() { return TYPE; }
    @Override public int order() { return 10; }
    @Override public boolean isCatalog() { return true; }
    @Override public Class<UserReplica> payloadType() { return UserReplica.class; }

    @Override
    public void apply(String key, UserReplica payload, Long version) {
        userRepository.save(payload.toDomain());
    }

    /** Accounts are never deleted, so no deletion is ever sent. */
    @Override
    public void delete(String key) {
        // nothing to delete
    }

    @Override
    public List<Item<UserReplica>> exportAll() {
        return userRepository.findAll().stream()
                .map(u -> new Item<>(u.getUsername(), UserReplica.from(u), null))
                .toList();
    }
}
