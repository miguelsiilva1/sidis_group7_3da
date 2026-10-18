package aisafe.shared.infrastructure.replication;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * What this replica knows about one replicated entity: its home (records only), the stamp of
 * its last change, and whether that change deleted it.
 *
 * <p>A stamp is the time of the change on the replica that made it, plus that replica's id to
 * break ties. The change with the latest stamp wins on every replica ("last writer wins"), for
 * saves and deletions alike. A deletion is kept as a tombstone, so a copy that missed it can
 * never bring the entity back, while saving it again later (a newer stamp) does.</p>
 */
@Entity
@Table(name = "replica_state")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReplicaState {

    @Id
    @Column(length = 600)
    private String id;

    @Column(nullable = false)
    private String type;

    @Column(name = "entity_key", nullable = false, length = 500)
    private String key;

    private String home;

    private long stamp;

    private String stampNode;

    private boolean deleted;

    ReplicaState(String type, String key) {
        this.id = idOf(type, key);
        this.type = type;
        this.key = key;
        this.stampNode = "";
    }

    static String idOf(String type, String key) {
        return type + ":" + key;
    }

    void changed(String home, long stamp, String stampNode, boolean deleted) {
        if (home != null) {
            this.home = home;
        }
        this.stamp = stamp;
        this.stampNode = stampNode == null ? "" : stampNode;
        this.deleted = deleted;
    }

    /** Whether a change with this stamp happened after the last change known here. */
    boolean isOlderThan(Long otherStamp, String otherNode) {
        return compare(otherStamp == null ? 0 : otherStamp, otherNode, stamp, stampNode) > 0;
    }

    static int compare(long stampA, String nodeA, long stampB, String nodeB) {
        int byTime = Long.compare(stampA, stampB);
        return byTime != 0 ? byTime : (nodeA == null ? "" : nodeA).compareTo(nodeB == null ? "" : nodeB);
    }
}
