package aisafe.shared.infrastructure.replication;

import tools.jackson.databind.JsonNode;

/**
 * A replica as sent between replicas: the entity key, its home replica (records only), the
 * sender's version (records only, for the ETag), the stamp of the change (time and replica),
 * whether the change deleted the entity, and the payload (none for a deletion).
 *
 * <p>An envelope from an older replica has no stamp and no deletion flag: it counts as a save
 * with stamp 0, so a replica being upgraded can still read its peers.</p>
 */
public record ReplicaEnvelope(String key, String home, Long version, JsonNode payload,
                              Long stamp, String stampNode, Boolean deleted) {

    public ReplicaEnvelope {
        deleted = Boolean.TRUE.equals(deleted);
    }
}
