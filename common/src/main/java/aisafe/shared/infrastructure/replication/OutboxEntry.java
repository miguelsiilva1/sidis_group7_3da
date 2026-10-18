package aisafe.shared.infrastructure.replication;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * A replication command waiting to be delivered to a peer (transactional outbox).
 *
 * <p>It is written in the same transaction as the change it replicates, so a change is never
 * committed without its command, and the command survives until the peer acknowledges it.
 * Commands to the same peer are delivered in the order they were written.</p>
 */
@Entity
@Table(name = "replication_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEntry {

    private static final Duration FIRST_RETRY = Duration.ofMillis(500);
    private static final Duration MAX_RETRY = Duration.ofSeconds(30);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String targetNode;

    @Column(nullable = false)
    private String method;

    @Column(nullable = false, length = 1000)
    private String path;

    @Lob
    private String body;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant nextAttemptAt;

    private int attempts;

    @Column(length = 500)
    private String lastError;

    public OutboxEntry(String targetNode, String method, String path, String body) {
        this.targetNode = targetNode;
        this.method = method;
        this.path = path;
        this.body = body;
        this.createdAt = Instant.now();
        this.nextAttemptAt = this.createdAt;
    }

    /** Records a failed delivery and schedules the next attempt with exponential backoff. */
    void failed(Instant now, String error) {
        attempts++;
        long delayMs = Math.min(FIRST_RETRY.toMillis() << Math.min(attempts - 1, 10), MAX_RETRY.toMillis());
        nextAttemptAt = now.plusMillis(delayMs);
        lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
    }
}
