package aisafe.flights.infrastructure.persistence.jpa;

import aisafe.flights.domain.RegistrationNumber;
import aisafe.flights.domain.ScheduledFlight;
import aisafe.flights.domain.ScheduledFlightRepository;
import aisafe.flights.infrastructure.replication.FlightReplica;
import aisafe.flights.infrastructure.replication.FlightReplicaHandler;
import aisafe.shared.infrastructure.replication.Replicator;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@Profile("jpa")
public class ScheduledFlightJpaRepository implements ScheduledFlightRepository {

    private final SpringDataScheduledFlightRepository springRepo;
    private final Replicator replicator;
    private final JdbcTemplate jdbcTemplate;

    public ScheduledFlightJpaRepository(SpringDataScheduledFlightRepository springRepo,
                                        Replicator replicator,
                                        JdbcTemplate jdbcTemplate) {
        this.springRepo = springRepo;
        this.replicator = replicator;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long count() {
        return springRepo.count();
    }

    @Override
    public ScheduledFlight save(ScheduledFlight flight) {
        Optional<ScheduledFlightJpaEntity> existing = Optional.empty();
        if (flight.getId() != null) {
            existing = springRepo.findById(flight.getId());
        }
        if (existing.isEmpty()) {
            existing = springRepo.findByAircraftRegistrationAndDepartureDateTime(
                    flight.getAircraftRegistrationNumber().getNumber(),
                    flight.getDepartureDateTime());
        }

        if (existing.isPresent()) {
            ScheduledFlightJpaEntity entity = existing.get();
            entity.setDepartureDateTime(flight.getDepartureDateTime());
            entity.setArrivalDateTime(flight.getArrivalDateTime());
            entity.setStatus(flight.getStatus());
            entity.setOriginIataCode(flight.getOriginCode().getCode());
            entity.setDestinationIataCode(flight.getDestinationCode().getCode());
            entity.setAircraftRegistration(flight.getAircraftRegistrationNumber().getNumber());
            if (flight.getVersion() != null) {
                entity.setVersion(flight.getVersion());
            }
            ScheduledFlightJpaEntity saved = springRepo.saveAndFlush(entity);
            ScheduledFlight result = ScheduledFlightMapper.toDomain(saved);
            replicator.recordSaved(FlightReplicaHandler.TYPE, String.valueOf(saved.getId()), FlightReplica.from(result), saved.getVersion());
            return result;
        } else if (flight.getId() != null) {
            // Replicated or imported flight with an explicit ID: insert directly with specified ID
            jdbcTemplate.update(
                    "INSERT INTO scheduled_flight (id, departure_date_time, arrival_date_time, status, origin_iata_code, destination_iata_code, aircraft_registration, version) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    flight.getId(),
                    flight.getDepartureDateTime(),
                    flight.getArrivalDateTime(),
                    flight.getStatus().name(),
                    flight.getOriginCode().getCode(),
                    flight.getDestinationCode().getCode(),
                    flight.getAircraftRegistrationNumber().getNumber(),
                    flight.getVersion() != null ? flight.getVersion() : 0L
            );
            ScheduledFlightJpaEntity saved = springRepo.findById(flight.getId()).orElseThrow();
            ScheduledFlight result = ScheduledFlightMapper.toDomain(saved);
            replicator.recordSaved(FlightReplicaHandler.TYPE, String.valueOf(saved.getId()), FlightReplica.from(result), saved.getVersion());
            return result;
        } else {
            ScheduledFlightJpaEntity entity = new ScheduledFlightJpaEntity(
                    flight.getDepartureDateTime(),
                    flight.getArrivalDateTime(),
                    flight.getStatus(),
                    flight.getOriginCode().getCode(),
                    flight.getDestinationCode().getCode(),
                    flight.getAircraftRegistrationNumber().getNumber()
            );
            ScheduledFlightJpaEntity saved = springRepo.saveAndFlush(entity);
            ScheduledFlight result = ScheduledFlightMapper.toDomain(saved);
            replicator.recordSaved(FlightReplicaHandler.TYPE, String.valueOf(saved.getId()), FlightReplica.from(result), saved.getVersion());
            return result;
        }
    }

    @Override
    public void delete(ScheduledFlight flight) {
        if (flight.getId() != null) {
            springRepo.findById(flight.getId()).ifPresent(entity -> {
                springRepo.delete(entity);
                replicator.recordDeleted(FlightReplicaHandler.TYPE, String.valueOf(entity.getId()));
            });
        }
    }

    @Override
    public Optional<ScheduledFlight> findById(Long id) {
        return springRepo.findById(id).map(ScheduledFlightMapper::toDomain);
    }

    @Override
    public Optional<ScheduledFlight> findByAircraftRegistrationAndDepartureDateTime(RegistrationNumber registration, OffsetDateTime departureDateTime) {
        return springRepo.findByAircraftRegistrationAndDepartureDateTime(registration.getNumber(), departureDateTime)
                .map(ScheduledFlightMapper::toDomain);
    }

    @Override
    public List<ScheduledFlight> findAll() {
        return springRepo.findAll().stream()
                .map(ScheduledFlightMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Long findVersionFor(Long id) {
        return springRepo.findVersionFor(id);
    }
}
