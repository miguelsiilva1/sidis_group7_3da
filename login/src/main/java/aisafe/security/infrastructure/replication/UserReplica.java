package aisafe.security.infrastructure.replication;

import aisafe.security.domain.Role;
import aisafe.security.domain.User;

import java.util.UUID;

/**
 * An account as copied between the logins: the password goes only as its hash.
 */
public record UserReplica(UUID userID, String username, String passwordHash, Role role) {

    public static UserReplica from(User user) {
        return new UserReplica(user.getUserID(), user.getUsername(), user.getPassword(), user.getRole());
    }

    public User toDomain() {
        return User.reconstitute(userID, username, passwordHash, role);
    }
}
