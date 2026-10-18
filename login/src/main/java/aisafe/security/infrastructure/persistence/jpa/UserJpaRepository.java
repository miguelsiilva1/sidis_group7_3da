package aisafe.security.infrastructure.persistence.jpa;

import aisafe.security.domain.User;
import aisafe.security.domain.UserRepository;
import aisafe.security.infrastructure.replication.UserReplica;
import aisafe.security.infrastructure.replication.UserReplicaHandler;
import aisafe.shared.infrastructure.replication.Replicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Profile("jpa")
public class UserJpaRepository implements UserRepository {

    private final SpringDataUserRepository springRepo;
    private final Replicator replicator;

    public UserJpaRepository(SpringDataUserRepository springRepo, Replicator replicator) {
        this.springRepo = springRepo;
        this.replicator = replicator;
    }

    @Override
    public long count() {
        return springRepo.count();
    }

    @Override
    public User save(User user) {
        Optional<UserJpaEntity> existing = springRepo.findByUsername(user.getUsername());
        if (existing.isPresent() && existing.get().getUserID().equals(user.getUserID())) {
            existing.get().changeCredentials(user.getPassword(), user.getRole());
        } else {
            // The same username created on two logins at once: the newest account replaces the other
            existing.ifPresent(e -> {
                springRepo.delete(e);
                springRepo.flush();
            });
            springRepo.save(UserMapper.toJpa(user));
        }
        // Accounts: copied to every login
        replicator.catalogSaved(UserReplicaHandler.TYPE, user.getUsername(), UserReplica.from(user));
        return user;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return springRepo.findByUsername(username).map(UserMapper::toDomain);
    }

    @Override
    public List<User> findAll() {
        return springRepo.findAll().stream().map(UserMapper::toDomain).toList();
    }
}
