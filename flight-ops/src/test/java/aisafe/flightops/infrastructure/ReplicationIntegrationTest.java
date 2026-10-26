package aisafe.flightops.infrastructure;

import aisafe.flights.application.FlightsAcrossReplicas;
import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.IataCode;
import aisafe.flights.domain.RegistrationNumber;
import aisafe.flights.domain.ScheduledFlight;
import aisafe.flights.domain.ScheduledFlightRepository;
import aisafe.flights.infrastructure.replication.FlightReplica;
import aisafe.flights.infrastructure.replication.FlightReplicaHandler;
import aisafe.shared.application.Versioned;
import aisafe.shared.infrastructure.replication.OutboxEntry;
import aisafe.shared.infrastructure.replication.OutboxRepository;
import aisafe.shared.infrastructure.replication.ReplicaApplier;
import aisafe.shared.infrastructure.replication.ReplicaEnvelope;
import aisafe.shared.infrastructure.replication.ReplicaExporter;
import aisafe.shared.infrastructure.replication.Replicator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This replica is flight-ops-0 in a ring of three (the peers do not exist: nothing is
 * delivered, the test only looks at what is written to the outbox).
 */
@SpringBootTest(properties = {
        "aisafe.node.id=flight-ops-0",
        "aisafe.peers=http://flight-ops-0.test.invalid:1,http://flight-ops-1.test.invalid:1,http://flight-ops-2.test.invalid:1",
        "aisafe.replication.dispatch-interval-ms=3600000"
})
@ActiveProfiles("jpa")
@Transactional
class ReplicationIntegrationTest {

    @Autowired private OutboxRepository outbox;
    @Autowired private ScheduledFlightRepository flightRepository;
    @Autowired private ReplicaApplier applier;
    @Autowired private FlightReplicaHandler flightHandler;
    @Autowired private Replicator replicator;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ReplicaExporter exporter;
    @Autowired private FlightsAcrossReplicas flightsAcrossReplicas;

    @BeforeEach
    void setUp() {
        outbox.deleteAll();
    }

    private ReplicaEnvelope fromPeer(String key, Long version, Object payload, long stamp) {
        return new ReplicaEnvelope(key, "flight-ops-2", version, objectMapper.valueToTree(payload), stamp,
                "flight-ops-2", false);
    }

    private List<String> targetsOf(String pathPrefix) {
        return outbox.findAll().stream()
                .filter(e -> e.getPath().startsWith(pathPrefix))
                .map(OutboxEntry::getTargetNode)
                .sorted()
                .toList();
    }

    private static FlightReplica flightReplica(Long id, String reg, String origin, String destination, FlightStatus status, Long version) {
        OffsetDateTime dep = OffsetDateTime.of(2026, 3, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime arr = dep.plusHours(2);
        return new FlightReplica(id, dep, arr, status, origin, destination, reg, version);
    }

    @Test
    void ensureNewFlightIsSentToTheSuccessorOnly() {
        OffsetDateTime dep = OffsetDateTime.of(2026, 4, 1, 8, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime arr = dep.plusHours(2);
        ScheduledFlight saved = flightRepository.save(new ScheduledFlight(
                dep, arr, FlightStatus.SCHEDULED, new IataCode("OPO"), new IataCode("LIS"), new RegistrationNumber("CS-TKA")));

        assertEquals(List.of("flight-ops-1"), targetsOf("/internal/replicas/flights"));
        assertEquals("flight-ops-0", replicator.homeOf(FlightReplicaHandler.TYPE, String.valueOf(saved.getId())).orElseThrow());
    }

    @Test
    void ensureReceivedFlightIsStoredWithTheSendersVersionAndNotSentAgain() {
        FlightReplica replica = flightReplica(100L, "CS-TKA", "FAO", "LIS", FlightStatus.SCHEDULED, 4L);
        applier.apply(flightHandler, fromPeer("100", 4L, replica, 1_000));

        ScheduledFlight stored = flightRepository.findById(100L).orElseThrow();
        assertEquals(FlightStatus.SCHEDULED, stored.getStatus());
        assertEquals(4L, stored.getVersion());
        assertEquals("flight-ops-2", replicator.homeOf(FlightReplicaHandler.TYPE, "100").orElseThrow());
        assertTrue(outbox.findAll().isEmpty(), "a received replica is not sent on");
    }

    @Test
    void ensureNewerReplicaReplacesTheFlightAndOlderOneIsIgnored() {
        FlightReplica newer = flightReplica(101L, "CS-TKA", "FAO", "LIS", FlightStatus.CANCELED, 2L);
        FlightReplica older = flightReplica(101L, "CS-TKA", "FAO", "LIS", FlightStatus.SCHEDULED, 1L);

        applier.apply(flightHandler, fromPeer("101", 2L, newer, 2_000));
        applier.apply(flightHandler, fromPeer("101", 1L, older, 1_000));

        ScheduledFlight stored = flightRepository.findById(101L).orElseThrow();
        assertEquals(FlightStatus.CANCELED, stored.getStatus());
    }

    @Test
    void ensureFlightsFromPeersAndFlightsCreatedHereNeverTakeEachOthersPlace() {
        // Each replica numbers its own database rows from 1: the flight's id must not come from them
        OffsetDateTime dep = OffsetDateTime.of(2026, 6, 1, 8, 0, 0, 0, ZoneOffset.UTC);
        ScheduledFlight local = flightRepository.save(new ScheduledFlight(
                dep, dep.plusHours(2), FlightStatus.SCHEDULED, new IataCode("OPO"), new IataCode("LIS"), new RegistrationNumber("CS-TKA")));
        long fromPeer = local.getId() + 1;   // the next id of flight-ops-1
        applier.apply(flightHandler, fromPeer(String.valueOf(fromPeer), 1L,
                flightReplica(fromPeer, "CS-TKB", "FAO", "LIS", FlightStatus.SCHEDULED, 1L), 1_000));
        ScheduledFlight later = flightRepository.save(new ScheduledFlight(
                dep.plusDays(1), dep.plusDays(1).plusHours(2), FlightStatus.SCHEDULED, new IataCode("OPO"), new IataCode("LIS"), new RegistrationNumber("CS-TKA")));

        assertEquals(1, local.getId() % 10, "flight-ops-0 gives the ids ending in 1");
        assertEquals(local.getId() + 10, later.getId());
        assertEquals("CS-TKA", flightRepository.findById(local.getId()).orElseThrow().getAircraftRegistrationNumber().getNumber());
        assertEquals("CS-TKB", flightRepository.findById(fromPeer).orElseThrow().getAircraftRegistrationNumber().getNumber());
        assertEquals(dep.plusDays(1), flightRepository.findById(later.getId()).orElseThrow().getDepartureDateTime());
    }

    @Test
    void ensureGatheredFlightsIncludeThoseHeldHereWhenPeersDoNotAnswer() {
        OffsetDateTime dep = OffsetDateTime.of(2026, 5, 1, 8, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime arr = dep.plusHours(2);
        ScheduledFlight saved = flightRepository.save(new ScheduledFlight(
                dep, arr, FlightStatus.SCHEDULED, new IataCode("OPO"), new IataCode("CDG"), new RegistrationNumber("CS-TKB")));

        List<Versioned<ScheduledFlight>> all = flightsAcrossReplicas.findAll();

        assertTrue(all.stream().anyMatch(v -> v.value().getId().equals(saved.getId())));
    }

    @Test
    void ensureDeletionLeavesATombstoneThatStopsAStaleCopyFromBringingItBack() {
        FlightReplica replica = flightReplica(102L, "CS-TKC", "LIS", "MAD", FlightStatus.SCHEDULED, 0L);
        applier.apply(flightHandler, fromPeer("102", 0L, replica, 1_000));
        ScheduledFlight stored = flightRepository.findById(102L).orElseThrow();

        flightRepository.delete(stored);
        boolean revived = applier.apply(flightHandler, fromPeer("102", 0L, replica, 1_000));

        assertFalse(revived);
        assertFalse(flightRepository.findById(102L).isPresent());
        List<ReplicaEnvelope> exported = exporter.export(flightHandler, "flight-ops-2");
        assertTrue(exported.stream().anyMatch(e -> e.key().equals("102") && e.deleted()),
                "the tombstone is exported to the other holder");
        assertEquals(List.of("flight-ops-2"), targetsOf("/internal/replicas/flights"),
                "this replica holds the copy: the deletion goes to the flight's home");
    }
}
