package aisafe.flights.infrastructure.replication;

import aisafe.flights.domain.ScheduledFlight;
import aisafe.flights.domain.ScheduledFlightRepository;
import aisafe.flights.infrastructure.persistence.jpa.SpringDataScheduledFlightRepository;
import aisafe.shared.infrastructure.replication.ReplicaHandler;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Applies scheduled flight replicas (record). Which change wins is decided
 * before, by its stamp ({@code ReplicaApplier}). After applying, the local version is set to the
 * sender's, so both holders give the same version.
 */
@Component
public class FlightReplicaHandler implements ReplicaHandler<FlightReplica> {

    public static final String TYPE = "flights";

    private final ScheduledFlightRepository scheduledFlightRepository;
    private final SpringDataScheduledFlightRepository springRepo;

    public FlightReplicaHandler(ScheduledFlightRepository scheduledFlightRepository,
                                SpringDataScheduledFlightRepository springRepo) {
        this.scheduledFlightRepository = scheduledFlightRepository;
        this.springRepo = springRepo;
    }

    @Override public String type() { return TYPE; }
    @Override public int order() { return 50; }
    @Override public boolean isCatalog() { return false; }
    @Override public Class<FlightReplica> payloadType() { return FlightReplica.class; }

    @Override
    public void apply(String key, FlightReplica payload, Long version) {
        ScheduledFlight saved = scheduledFlightRepository.save(payload.toDomain());
        if (version != null) {
            if (saved.getId() != null) {
                springRepo.alignVersion(saved.getId(), version);
            }
            try {
                springRepo.alignVersion(Long.valueOf(key), version);
            } catch (NumberFormatException ignored) {}
            springRepo.alignVersionByNaturalKey(payload.aircraftRegistration(), payload.departureDateTime(), version);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Long id = Long.valueOf(key);
            scheduledFlightRepository.findById(id).ifPresent(scheduledFlightRepository::delete);
        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    public List<Item<FlightReplica>> exportAll() {
        return scheduledFlightRepository.findAll().stream()
                .map(flight -> new Item<>(
                        String.valueOf(flight.getId()),
                        FlightReplica.from(flight),
                        flight.getVersion()))
                .toList();
    }
}
