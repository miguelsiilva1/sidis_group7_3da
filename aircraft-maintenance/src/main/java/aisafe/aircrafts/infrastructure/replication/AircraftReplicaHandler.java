package aisafe.aircrafts.infrastructure.replication;

import aisafe.aircrafts.domain.AircraftModel;
import aisafe.aircrafts.domain.AircraftModelNotFoundException;
import aisafe.aircrafts.domain.AircraftModelRepository;
import aisafe.aircrafts.domain.AircraftRepository;
import aisafe.aircrafts.domain.RegistrationNumber;
import aisafe.aircrafts.infrastructure.persistence.jpa.SpringDataAircraftRepository;
import aisafe.shared.infrastructure.replication.ReplicaHandler;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Applies aircraft replicas (record). Which change wins is decided before, by its stamp
 * ({@code ReplicaApplier}). After applying, the local version is set to the sender's, so both
 * holders give the same ETag.
 */
@Component
public class AircraftReplicaHandler implements ReplicaHandler<AircraftReplica> {

    public static final String TYPE = "aircrafts";

    private final AircraftRepository aircraftRepository;
    private final AircraftModelRepository modelRepository;
    private final SpringDataAircraftRepository springRepo;

    public AircraftReplicaHandler(AircraftRepository aircraftRepository, AircraftModelRepository modelRepository,
                                  SpringDataAircraftRepository springRepo) {
        this.aircraftRepository = aircraftRepository;
        this.modelRepository = modelRepository;
        this.springRepo = springRepo;
    }

    @Override public String type() { return TYPE; }
    @Override public int order() { return 40; }
    @Override public boolean isCatalog() { return false; }
    @Override public Class<AircraftReplica> payloadType() { return AircraftReplica.class; }

    @Override
    public void apply(String key, AircraftReplica payload, Long version) {
        AircraftModel model = modelRepository.findByModelName(payload.modelName())
                .orElseThrow(() -> new AircraftModelNotFoundException("Aircraft model not found: " + payload.modelName()));
        aircraftRepository.save(payload.toDomain(model));
        if (version != null) {
            springRepo.alignVersion(key, version);
        }
    }

    @Override
    public void delete(String key) {
        aircraftRepository.findByRegistrationNumber(new RegistrationNumber(key)).ifPresent(aircraftRepository::delete);
    }

    @Override
    public List<Item<AircraftReplica>> exportAll() {
        return aircraftRepository.findAll().stream()
                .map(a -> new Item<>(a.getRegistrationNumber().getNumber(), AircraftReplica.from(a),
                        aircraftRepository.findVersionFor(a.getRegistrationNumber())))
                .toList();
    }
}
