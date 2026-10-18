package aisafe.airports.infrastructure.replication;

import aisafe.airports.domain.AirportRepository;
import aisafe.airports.domain.IataCode;
import aisafe.shared.infrastructure.replication.ReplicaHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Applies airport replicas (catalog).
 */
@Component
public class AirportReplicaHandler implements ReplicaHandler<AirportReplica> {

    public static final String TYPE = "airports";
    private static final Logger log = LoggerFactory.getLogger(AirportReplicaHandler.class);

    private final AirportRepository airportRepository;

    public AirportReplicaHandler(AirportRepository airportRepository) {
        this.airportRepository = airportRepository;
    }

    @Override public String type() { return TYPE; }
    @Override public int order() { return 10; }
    @Override public boolean isCatalog() { return true; }
    @Override public Class<AirportReplica> payloadType() { return AirportReplica.class; }

    @Override
    public void apply(String key, AirportReplica payload, Long version) {
        airportRepository.save(payload.toDomain());
    }

    @Override
    public void delete(String key) {
        try {
            airportRepository.findByIataCode(new IataCode(key)).ifPresent(airportRepository::delete);
        } catch (DataIntegrityViolationException e) {
            // A certification on this replica still uses the airport: keep it rather than block replication.
            log.warn("Airport {} not deleted on this replica: still in use", key);
        }
    }

    @Override
    public List<Item<AirportReplica>> exportAll() {
        return airportRepository.findAll().stream()
                .map(a -> new Item<>(a.getIataCode().getCode(), AirportReplica.from(a), null))
                .toList();
    }
}
