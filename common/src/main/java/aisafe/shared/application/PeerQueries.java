package aisafe.shared.application;

import java.util.List;
import java.util.Optional;

/**
 * Port used by use cases to read data held by the other replicas of the same service.
 *
 * <p>Data is partitioned between replicas: a record lives on the replica that created it.
 * A use case that needs data it does not hold locally asks its peers through this port.
 * Peers that do not answer are skipped, and the response is marked as partial (AP: the
 * service stays available during partial failures).</p>
 *
 * <p>When the current request was itself forwarded by a peer, the port never forwards again
 * and returns nothing, so requests never loop between replicas.</p>
 */
public interface PeerQueries {

    /**
     * Asks every peer for the list at {@code path} (an internal, local-only route) and returns all
     * the elements together.
     */
    <T> List<T> fetchFromAll(String path, Class<T> elementType);

    /**
     * Asks every peer for the resource at {@code path} and returns the first one found.
     */
    <T> Optional<T> fetchFirst(String path, Class<T> type);
}
