package aisafe.shared.infrastructure.replication;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on the scheduled delivery of the replication outbox.
 */
@Configuration
@EnableScheduling
public class ReplicationConfig {
}
