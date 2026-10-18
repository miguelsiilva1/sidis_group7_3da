package aisafe.flightcopies.infrastructure.replication;

import aisafe.flightcopies.domain.FlightCopy;
import aisafe.flightcopies.domain.FlightCopyRepository;
import aisafe.shared.infrastructure.replication.ReplicaHandler;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Applies flight copy replicas (record). A replica older than or as old as the stored copy is
 * ignored, the same rule as for copies sent by flight-ops.
 */
@Component
public class FlightCopyReplicaHandler implements ReplicaHandler<FlightCopyReplica> {

    public static final String TYPE = "flight-copies";

    private final FlightCopyRepository flightCopyRepository;

    public FlightCopyReplicaHandler(FlightCopyRepository flightCopyRepository) {
        this.flightCopyRepository = flightCopyRepository;
    }

    @Override public String type() { return TYPE; }
    @Override public int order() { return 60; }
    @Override public boolean isCatalog() { return false; }
    @Override public Class<FlightCopyReplica> payloadType() { return FlightCopyReplica.class; }

    @Override
    public void apply(String key, FlightCopyReplica payload, Long version) {
        FlightCopy copy = payload.toDomain();
        boolean newer = flightCopyRepository.findById(key).map(copy::supersedes).orElse(true);
        if (newer) {
            flightCopyRepository.save(copy);
        }
    }

    @Override
    public void delete(String key) {
        flightCopyRepository.findById(key).ifPresent(flightCopyRepository::delete);
    }

    @Override
    public List<Item<FlightCopyReplica>> exportAll() {
        return flightCopyRepository.findAll().stream()
                .map(f -> new Item<>(f.getId(), FlightCopyReplica.from(f), f.getVersion()))
                .toList();
    }
}
