package aisafe.airports.infrastructure.persistence.jpa;

import aisafe.airports.domain.AircraftCertification;
import aisafe.airports.domain.AircraftCertificationRepository;
import aisafe.airports.domain.AirportNotFoundException;
import aisafe.airports.domain.IataCode;
import aisafe.airports.domain.ModelName;
import aisafe.airports.infrastructure.replication.AircraftCertificationReplica;
import aisafe.airports.infrastructure.replication.AircraftCertificationReplicaHandler;
import aisafe.shared.infrastructure.replication.Replicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
@Profile("jpa")
public class AircraftCertificationJpaRepository implements AircraftCertificationRepository {

    private final SpringDataAircraftCertificationRepository springRepo;
    private final SpringDataAirportRepository airportSpringRepo;
    private final Replicator replicator;

    public AircraftCertificationJpaRepository(SpringDataAircraftCertificationRepository springRepo,
                                               SpringDataAirportRepository airportSpringRepo,
                                               Replicator replicator) {
        this.springRepo = springRepo;
        this.airportSpringRepo = airportSpringRepo;
        this.replicator = replicator;
    }

    @Override
    public List<AircraftCertification> findByAirportCode(IataCode airportCode) {
        AirportJpaEntity jpaAirport = airportSpringRepo.findByIataCode(new IataCodeJpaEmbeddable(airportCode.getCode()))
                .orElseThrow(() -> new AirportNotFoundException(airportCode.getCode()));

        return springRepo.findByAirport(jpaAirport).stream()
                .map(AircraftCertificationMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsByAirportCodeAndAircraftModelName(IataCode airportCode, ModelName aircraftModelName) {
        AirportJpaEntity jpaAirport = airportSpringRepo.findByIataCode(new IataCodeJpaEmbeddable(airportCode.getCode()))
                .orElse(null);
        if (jpaAirport == null) return false;
        return springRepo.existsByAirportAndAircraftModelName(jpaAirport, aircraftModelName.getName());
    }

    @Override
    public long count() {
        return springRepo.count();
    }

    @Override
    public List<AircraftCertification> findAll() {
        return springRepo.findAll().stream()
                .map(AircraftCertificationMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public AircraftCertification save(AircraftCertification certification) {
        AirportJpaEntity jpaAirport = airportSpringRepo.findByIataCode(
                        new IataCodeJpaEmbeddable(certification.getAirportCode().getCode()))
                .orElseThrow(() -> new AirportNotFoundException(
                        certification.getAirportCode().getCode()));

        springRepo.saveAndFlush(new AircraftCertificationJpaEntity(jpaAirport, certification.getAircraftModelName().getName()));
        // Catalog: copied to every other replica
        replicator.catalogSaved(AircraftCertificationReplicaHandler.TYPE, AircraftCertificationReplica.keyOf(certification),
                AircraftCertificationReplica.from(certification));
        return certification;
    }

    @Override
    public void delete(AircraftCertification certification) {
        AirportJpaEntity jpaAirport = airportSpringRepo.findByIataCode(
                        new IataCodeJpaEmbeddable(certification.getAirportCode().getCode()))
                .orElseThrow(() -> new AirportNotFoundException(
                        certification.getAirportCode().getCode()));

        springRepo.findByAirport(jpaAirport).stream()
                .filter(e -> e.getAircraftModelName().equals(certification.getAircraftModelName().getName()))
                .findFirst()
                .ifPresent(entity -> {
                    springRepo.delete(entity);
                    replicator.catalogDeleted(AircraftCertificationReplicaHandler.TYPE, AircraftCertificationReplica.keyOf(certification));
                });
    }
}
