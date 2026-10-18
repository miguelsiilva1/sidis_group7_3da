package aisafe.aircraftmaintenance.infrastructure;

import aisafe.aircrafts.application.AircraftAcrossReplicas;
import aisafe.aircrafts.domain.*;
import aisafe.flightcopies.domain.FlightCopy;
import aisafe.flightcopies.domain.FlightCopyRepository;
import aisafe.flightcopies.domain.FlightCopyStatus;
import aisafe.aircrafts.infrastructure.replication.AircraftReplica;
import aisafe.aircrafts.infrastructure.replication.AircraftReplicaHandler;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This replica is aircraft-maintenance-0 in a ring of three (the peers do not exist: nothing is
 * delivered, the test only looks at what is written to the outbox).
 */
@SpringBootTest(properties = {
        "aisafe.node.id=aircraft-maintenance-0",
        "aisafe.peers=http://aircraft-maintenance-0.test.invalid:1,http://aircraft-maintenance-1.test.invalid:1,http://aircraft-maintenance-2.test.invalid:1",
        "aisafe.replication.dispatch-interval-ms=3600000"
})
@ActiveProfiles("jpa")
@Transactional
class ReplicationIntegrationTest {

    @Autowired private OutboxRepository outbox;
    @Autowired private AircraftModelRepository modelRepository;
    @Autowired private AircraftRepository aircraftRepository;
    @Autowired private ReplicaApplier applier;
    @Autowired private AircraftReplicaHandler aircraftHandler;
    @Autowired private Replicator replicator;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ReplicaExporter exporter;
    @Autowired private FlightCopyRepository flightCopyRepository;
    @Autowired private AircraftAcrossReplicas aircraftAcrossReplicas;

    private AircraftModel a320;

    @BeforeEach
    void setUp() {
        outbox.deleteAll();
        a320 = modelRepository.findByModelName("Airbus A320neo").orElseThrow();
    }

    private ReplicaEnvelope fromPeer(String key, long version, Object payload, long stamp) {
        return new ReplicaEnvelope(key, "aircraft-maintenance-2", version, objectMapper.valueToTree(payload), stamp,
                "aircraft-maintenance-2", false);
    }

    private List<String> targetsOf(String pathPrefix) {
        return outbox.findAll().stream()
                .filter(e -> e.getPath().startsWith(pathPrefix))
                .map(OutboxEntry::getTargetNode)
                .sorted()
                .toList();
    }

    @Test
    void ensureNewModelIsSentToEveryOtherReplica() {
        modelRepository.save(new AircraftModel("Test Model X", Manufacturer.AIRBUS, 1000.0, 3000.0, 700.0, null, 100));

        assertEquals(List.of("aircraft-maintenance-1", "aircraft-maintenance-2"),
                targetsOf("/internal/replicas/aircraft-models"));
    }

    @Test
    void ensureNewAircraftIsSentToTheSuccessorOnly() {
        aircraftRepository.save(new Aircraft(AircraftStatus.AVAILABLE, LocalDate.of(2022, 1, 1), a320,
                new RegistrationNumber("CS-REP"), 150, 5000.0, List.of()));

        assertEquals(List.of("aircraft-maintenance-1"), targetsOf("/internal/replicas/aircrafts"));
        assertEquals("aircraft-maintenance-0", replicator.homeOf(AircraftReplicaHandler.TYPE, "CS-REP").orElseThrow());
    }

    @Test
    void ensureReceivedReplicaIsStoredWithTheSendersVersionAndNotSentAgain() {
        AircraftReplica replica = new AircraftReplica("CS-RCV", AircraftStatus.AVAILABLE, LocalDate.of(2021, 5, 1),
                "Airbus A320neo", 140, 5500.0, List.of("WiFi"));

        applier.apply(aircraftHandler, fromPeer("CS-RCV", 4L, replica, 1_000));

        RegistrationNumber registration = new RegistrationNumber("CS-RCV");
        assertTrue(aircraftRepository.existsByRegistrationNumber(registration));
        assertEquals(4L, aircraftRepository.findVersionFor(registration));
        assertEquals("aircraft-maintenance-2", replicator.homeOf(AircraftReplicaHandler.TYPE, "CS-RCV").orElseThrow());
        assertTrue(outbox.findAll().isEmpty(), "a received replica is not sent on");
    }

    @Test
    void ensureOlderChangeIsIgnored() {
        AircraftReplica v5 = new AircraftReplica("CS-OLD", AircraftStatus.AVAILABLE, LocalDate.of(2021, 5, 1),
                "Airbus A320neo", 140, 5500.0, List.of());
        AircraftReplica v3 = new AircraftReplica("CS-OLD", AircraftStatus.INACTIVE, LocalDate.of(2021, 5, 1),
                "Airbus A320neo", 140, 5500.0, List.of());

        applier.apply(aircraftHandler, fromPeer("CS-OLD", 5L, v5, 2_000));
        applier.apply(aircraftHandler, fromPeer("CS-OLD", 3L, v3, 1_000));

        Aircraft stored = aircraftRepository.findByRegistrationNumber(new RegistrationNumber("CS-OLD")).orElseThrow();
        assertEquals(AircraftStatus.AVAILABLE, stored.getStatus());
    }

    @Test
    void ensureChangeOnTheSuccessorGoesBackToTheHome() {
        AircraftReplica replica = new AircraftReplica("CS-UPD", AircraftStatus.AVAILABLE, LocalDate.of(2021, 5, 1),
                "Airbus A320neo", 140, 5500.0, List.of());
        applier.apply(aircraftHandler, fromPeer("CS-UPD", 0L, replica, 1_000));

        Aircraft stored = aircraftRepository.findByRegistrationNumber(new RegistrationNumber("CS-UPD")).orElseThrow();
        aircraftRepository.save(new Aircraft(AircraftStatus.UNDER_MAINTENANCE, stored.getManufacturingDate(), a320,
                stored.getRegistrationNumber(), stored.getSeatCapacity(), stored.getRange(), stored.getFeatures()));

        assertEquals(List.of("aircraft-maintenance-2"), targetsOf("/internal/replicas/aircrafts"));
    }

    @Test
    void ensureNewFlightCopyIsSentToTheSuccessorOnly() {
        OffsetDateTime departure = OffsetDateTime.of(2025, 3, 1, 8, 0, 0, 0, ZoneOffset.UTC);
        flightCopyRepository.save(new FlightCopy("F-REP", departure, departure.plusHours(2), FlightCopyStatus.SCHEDULED,
                "OPO", "LIS", new RegistrationNumber("CS-TKA"), 1));

        assertEquals(List.of("aircraft-maintenance-1"), targetsOf("/internal/replicas/flight-copies"));
    }

    @Test
    void ensureGatheredAircraftIncludeThoseHeldHereWhenPeersDoNotAnswer() {
        aircraftRepository.save(new Aircraft(AircraftStatus.AVAILABLE, LocalDate.of(2022, 1, 1), a320,
                new RegistrationNumber("CS-GAT"), 150, 5000.0, List.of()));

        List<Aircraft> all = aircraftAcrossReplicas.findAll();

        assertTrue(all.stream().anyMatch(a -> a.getRegistrationNumber().getNumber().equals("CS-GAT")
                && a.getModel().getModelName().equals("Airbus A320neo")));
        List<String> registrations = all.stream().map(a -> a.getRegistrationNumber().getNumber()).toList();
        assertEquals(registrations.stream().sorted().toList(), registrations);
    }

    @Test
    void ensureDeletionLeavesATombstoneThatStopsAStaleCopyFromBringingItBack() {
        AircraftReplica replica = new AircraftReplica("CS-DEL", AircraftStatus.AVAILABLE, LocalDate.of(2021, 5, 1),
                "Airbus A320neo", 140, 5500.0, List.of());
        applier.apply(aircraftHandler, fromPeer("CS-DEL", 0L, replica, 1_000));
        Aircraft stored = aircraftRepository.findByRegistrationNumber(new RegistrationNumber("CS-DEL")).orElseThrow();

        aircraftRepository.delete(stored);
        boolean revived = applier.apply(aircraftHandler, fromPeer("CS-DEL", 0L, replica, 1_000));

        assertFalse(revived);
        assertFalse(aircraftRepository.existsByRegistrationNumber(new RegistrationNumber("CS-DEL")));
        List<ReplicaEnvelope> exported = exporter.export(aircraftHandler, "aircraft-maintenance-2");
        assertTrue(exported.stream().anyMatch(e -> e.key().equals("CS-DEL") && e.deleted()),
                "the tombstone is exported to the other holder");
        assertEquals(List.of("aircraft-maintenance-2"), targetsOf("/internal/replicas/aircrafts"),
                "this replica holds the copy: the deletion goes to the record's home");
    }
}
