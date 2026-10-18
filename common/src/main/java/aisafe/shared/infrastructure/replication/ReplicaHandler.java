package aisafe.shared.infrastructure.replication;

import java.util.List;

/**
 * Applies the replicas of one kind of entity on the receiving replica. Each service registers
 * one handler per replicated entity.
 *
 * <ul>
 *   <li><b>Catalogs</b> (reference data, few and rarely changed) are copied to every replica.</li>
 *   <li><b>Records</b> (transactional data) are partitioned: each is held by its home replica and
 *   by the home's successor.</li>
 * </ul>
 *
 * @param <T> the replica payload: everything needed to rebuild the entity on another replica
 */
public interface ReplicaHandler<T> {

    /** Name used in the replication routes, e.g. {@code aircraft-models}. */
    String type();

    /** Recovery order: catalogs before the records that reference them. */
    int order();

    boolean isCatalog();

    Class<T> payloadType();

    /** Creates or replaces the entity. {@code version} is the sender's version (records only). */
    void apply(String key, T payload, Long version);

    /** Deletes the entity, if this replica has it. */
    void delete(String key);

    /** Every entity of this kind held by this replica, for a peer that is recovering. */
    List<Item<T>> exportAll();

    record Item<T>(String key, T payload, Long version) {
    }
}
