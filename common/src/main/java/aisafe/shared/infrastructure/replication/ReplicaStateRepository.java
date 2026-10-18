package aisafe.shared.infrastructure.replication;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReplicaStateRepository extends JpaRepository<ReplicaState, String> {

    /** Everything known about one kind of entity, tombstones included. */
    List<ReplicaState> findByType(String type);
}
