package aisafe.security.application;

import aisafe.security.domain.User;

import java.util.Optional;

/**
 * The accounts held by the other logins. Every login receives every account, but a few hundred
 * milliseconds after it was created (or later, if it was down): an account not found here may
 * already be on another login.
 */
public interface AccountsOnPeers {
    Optional<User> findByUsername(String username);
}
