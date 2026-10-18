package aisafe.flightcopies.infrastructure.persistence.jpa;

import aisafe.flightcopies.infrastructure.replication.FlightCopyReplica;
import aisafe.flightcopies.infrastructure.replication.FlightCopyReplicaHandler;
import aisafe.shared.infrastructure.replication.Replicator;
import aisafe.flightcopies.domain.FlightCopy;
import aisafe.flightcopies.domain.FlightCopyRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA adapter for the flight copies.
 */
@Repository
@Profile("jpa")
public class FlightCopyJpaRepository implements FlightCopyRepository {

    private final SpringDataFlightCopyRepository springRepo;
    private final Replicator replicator;

    public FlightCopyJpaRepository(SpringDataFlightCopyRepository springRepo, Replicator replicator) {
        this.springRepo = springRepo;
        this.replicator = replicator;
    }

    @Override
    public long count() {
        return springRepo.count();
    }

    @Override
    public List<FlightCopy> findAll() {
        return springRepo.findAll().stream().map(FlightCopyMapper::toDomain).toList();
    }

    @Override
    public FlightCopy save(FlightCopy copy) {
        FlightCopy saved = FlightCopyMapper.toDomain(springRepo.save(FlightCopyMapper.toJpa(copy)));
        // Record: copied to the other holder (home replica and its successor)
        replicator.recordSaved(FlightCopyReplicaHandler.TYPE, saved.getId(), FlightCopyReplica.from(saved), saved.getVersion());
        return saved;
    }

    @Override
    public void delete(FlightCopy copy) {
        springRepo.deleteById(copy.getId());
        replicator.recordDeleted(FlightCopyReplicaHandler.TYPE, copy.getId());
    }

    @Override
    public Optional<FlightCopy> findById(String id) {
        return springRepo.findById(id).map(FlightCopyMapper::toDomain);
    }

}
