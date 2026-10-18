package aisafe.login.infrastructure;

import aisafe.security.domain.Role;
import aisafe.security.domain.User;
import aisafe.security.domain.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Populates the database with the monolith's sample users on startup.
 *
 * <p>Every login holds a copy of every account, so only one login creates the sample users and
 * the others receive them: the one named in {@code aisafe.bootstrap.seed-node}. When that
 * property is empty (local development), the users are always created.</p>
 */
@Component
public class Bootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(Bootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String nodeId;
    private final String seedNode;

    public Bootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder,
            @Value("${aisafe.node.id}") String nodeId,
            @Value("${aisafe.bootstrap.seed-node:}") String seedNode) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.nodeId = nodeId;
        this.seedNode = seedNode;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedNode.isBlank() && !seedNode.equals(nodeId)) {
            log.info("Login {} does not create the sample users (seed node: {})", nodeId, seedNode);
            return;
        }
        if (userRepository.count() == 0) {
            userRepository.save(new User("admin", passwordEncoder.encode("admin123"), Role.ADMIN));
            userRepository.save(new User("operator", passwordEncoder.encode("operator123"), Role.BACKOFFICE_OPERATOR));
            userRepository.save(new User("atcc", passwordEncoder.encode("atcc123"), Role.ATCC));
            userRepository
                    .save(new User("technician", passwordEncoder.encode("technician123"), Role.MAINTENANCE_TECHNICIAN));
            userRepository
                    .save(new User("supervisor", passwordEncoder.encode("supervisor123"), Role.MAINTENANCE_SUPERVISOR));
        }
    }
}
