package aisafe.aircrafts.infrastructure.replication;

import aisafe.aircrafts.domain.AircraftModelRepository;
import aisafe.shared.infrastructure.replication.ReplicaHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Applies aircraft model replicas (catalog).
 */
@Component
public class AircraftModelReplicaHandler implements ReplicaHandler<AircraftModelReplica> {

    public static final String TYPE = "aircraft-models";
    private static final Logger log = LoggerFactory.getLogger(AircraftModelReplicaHandler.class);

    private final AircraftModelRepository modelRepository;

    public AircraftModelReplicaHandler(AircraftModelRepository modelRepository) {
        this.modelRepository = modelRepository;
    }

    @Override public String type() { return TYPE; }
    @Override public int order() { return 10; }
    @Override public boolean isCatalog() { return true; }
    @Override public Class<AircraftModelReplica> payloadType() { return AircraftModelReplica.class; }

    @Override
    public void apply(String key, AircraftModelReplica payload, Long version) {
        modelRepository.save(payload.toDomain());
    }

    @Override
    public void delete(String key) {
        try {
            modelRepository.findByModelName(key).ifPresent(modelRepository::delete);
        } catch (DataIntegrityViolationException e) {
            // An aircraft on this replica still uses the model: keep it rather than block replication.
            log.warn("Aircraft model {} not deleted on this replica: still in use", key);
        }
    }

    @Override
    public List<Item<AircraftModelReplica>> exportAll() {
        return modelRepository.findAll().stream()
                .map(m -> new Item<>(m.getModelName(), AircraftModelReplica.from(m), null))
                .toList();
    }
}
