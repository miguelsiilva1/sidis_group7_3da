package aisafe.airports.infrastructure.replication;

import aisafe.airports.domain.AircraftCertification;
import aisafe.airports.domain.AircraftCertificationRepository;
import aisafe.shared.infrastructure.replication.ReplicaHandler;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Applies certification replicas (catalog). The key is {@code <airport>:<model name>}.
 */
@Component
public class AircraftCertificationReplicaHandler implements ReplicaHandler<AircraftCertificationReplica> {

    public static final String TYPE = "aircraft-certifications";

    private final AircraftCertificationRepository certificationRepository;

    public AircraftCertificationReplicaHandler(AircraftCertificationRepository certificationRepository) {
        this.certificationRepository = certificationRepository;
    }

    @Override public String type() { return TYPE; }
    @Override public int order() { return 20; }
    @Override public boolean isCatalog() { return true; }
    @Override public Class<AircraftCertificationReplica> payloadType() { return AircraftCertificationReplica.class; }

    @Override
    public void apply(String key, AircraftCertificationReplica payload, Long version) {
        AircraftCertification certification = payload.toDomain();
        if (!certificationRepository.existsByAirportCodeAndAircraftModelName(
                certification.getAirportCode(), certification.getAircraftModelName())) {
            certificationRepository.save(certification);
        }
    }

    @Override
    public void delete(String key) {
        certificationRepository.findAll().stream()
                .filter(c -> AircraftCertificationReplica.keyOf(c).equals(key))
                .findFirst()
                .ifPresent(certificationRepository::delete);
    }

    @Override
    public List<Item<AircraftCertificationReplica>> exportAll() {
        return certificationRepository.findAll().stream()
                .map(c -> new Item<>(AircraftCertificationReplica.keyOf(c), AircraftCertificationReplica.from(c), null))
                .toList();
    }
}
