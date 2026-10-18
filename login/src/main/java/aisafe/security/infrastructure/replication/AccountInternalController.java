package aisafe.security.infrastructure.replication;

import aisafe.security.domain.UserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * One account held by this login, for another login that does not have it yet. Only the logins
 * call it (role SERVICE): the answer carries the password hash.
 */
@RestController
@RequestMapping(AccountInternalController.PATH)
@Tag(name = "Internal", description = "Routes used by other services, not by clients")
public class AccountInternalController {

    static final String PATH = "/internal/accounts";

    private final UserRepository userRepository;

    public AccountInternalController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/{username}")
    public ResponseEntity<UserReplica> account(@PathVariable String username) {
        return ResponseEntity.of(userRepository.findByUsername(username).map(UserReplica::from));
    }
}
