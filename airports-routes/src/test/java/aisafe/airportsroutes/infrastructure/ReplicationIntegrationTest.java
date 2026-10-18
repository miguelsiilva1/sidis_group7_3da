package aisafe.airportsroutes.infrastructure;

import aisafe.airports.domain.AircraftCertification;
import aisafe.airports.domain.AircraftCertificationRepository;
import aisafe.airports.domain.Airport;
import aisafe.airports.domain.AirportRepository;
import aisafe.airports.domain.IataCode;
import aisafe.airports.domain.ModelName;
import aisafe.airports.domain.Runway;
import aisafe.airports.infrastructure.replication.AirportReplica;
import aisafe.airports.infrastructure.replication.AirportReplicaHandler;
import aisafe.flightcopies.domain.FlightCopy;
import aisafe.flightcopies.domain.FlightCopyRepository;
import aisafe.flightcopies.domain.FlightCopyStatus;
import aisafe.routes.application.RoutesAcrossReplicas;
import aisafe.routes.domain.Route;
import aisafe.routes.domain.RouteHistory;
import aisafe.routes.domain.RouteHistoryRepository;
import aisafe.routes.domain.RouteRepository;
import aisafe.routes.domain.RouteStatus;
import aisafe.routes.infrastructure.replication.RouteReplica;
import aisafe.routes.infrastructure.replication.RouteReplicaHandler;
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

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This replica is airports-routes-0 in a ring of three (the peers do not exist: nothing is
 * delivered, the test only looks at what is written to the outbox).
 */
@SpringBootTest(properties = {
        "aisafe.node.id=airports-routes-0",
        "aisafe.peers=http://airports-routes-0.test.invalid:1,http://airports-routes-1.test.invalid:1,http://airports-routes-2.test.invalid:1",
        "aisafe.replication.dispatch-interval-ms=3600000"
})
@ActiveProfiles("jpa")
@Transactional
class ReplicationIntegrationTest {

    @Autowired private OutboxRepository outbox;
    @Autowired private AirportRepository airportRepository;
    @Autowired private AircraftCertificationRepository certificationRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private RouteHistoryRepository historyRepository;
    @Autowired private ReplicaApplier applier;
    @Autowired private RouteReplicaHandler routeHandler;
    @Autowired private AirportReplicaHandler airportHandler;
    @Autowired private Replicator replicator;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ReplicaExporter exporter;
    @Autowired private FlightCopyRepository flightCopyRepository;
    @Autowired private RoutesAcrossReplicas routesAcrossReplicas;

    @BeforeEach
    void setUp() {
        outbox.deleteAll();
    }

    private ReplicaEnvelope fromPeer(String key, Long version, Object payload, long stamp) {
        return new ReplicaEnvelope(key, "airports-routes-2", version, objectMapper.valueToTree(payload), stamp,
                "airports-routes-2", false);
    }

    private List<String> targetsOf(String pathPrefix) {
        return outbox.findAll().stream()
                .filter(e -> e.getPath().startsWith(pathPrefix))
                .map(OutboxEntry::getTargetNode)
                .sorted()
                .toList();
    }

    private static RouteReplica routeReplica(String origin, String destination, RouteStatus status, String... history) {
        LocalDateTime at = LocalDateTime.of(2026, 1, 1, 10, 0);
        return new RouteReplica(origin, destination, 45, 280.0, 100, status,
                java.util.Arrays.stream(history)
                        .map(h -> new RouteReplica.HistoryEntry(h, at, "atcc"))
                        .toList());
    }

    @Test
    void ensureNewAirportIsSentToEveryOtherReplica() {
        airportRepository.save(new Airport("FAO", "Faro Airport", "Faro", "Portugal", "Southern Europe",
                "Europe/Lisbon", 37.01, -7.96, List.of(new Runway("10/28", 2490, "100/280"))));

        assertEquals(List.of("airports-routes-1", "airports-routes-2"), targetsOf("/internal/replicas/airports"));
    }

    @Test
    void ensureNewCertificationIsSentToEveryOtherReplica() {
        certificationRepository.save(new AircraftCertification(new IataCode("LIS"), new ModelName("Airbus A320neo")));

        assertEquals(List.of("airports-routes-1", "airports-routes-2"),
                targetsOf("/internal/replicas/aircraft-certifications"));
    }

    @Test
    void ensureNewRouteIsSentToTheSuccessorOnly() {
        routeRepository.save(new Route("OPO", "CDG", 130, 1200.0, 100));

        assertEquals(List.of("airports-routes-1"), targetsOf("/internal/replicas/routes"));
        assertEquals("airports-routes-0", replicator.homeOf(RouteReplicaHandler.TYPE, "OPO-CDG").orElseThrow());
    }

    @Test
    void ensureHistoryEntryTravelsWithItsRoute() {
        routeRepository.save(new Route("OPO", "CDG", 130, 1200.0, 100));
        historyRepository.save(new RouteHistory(new IataCode("OPO"), new IataCode("CDG"), "Route created", "atcc"));

        List<OutboxEntry> sent = outbox.findAll().stream()
                .filter(e -> e.getPath().startsWith("/internal/replicas/routes"))
                .toList();
        assertEquals(2, sent.size(), "the route is copied again with the new history entry");
        assertTrue(sent.get(sent.size() - 1).getBody().contains("Route created"));
    }

    @Test
    void ensureReceivedRouteIsStoredWithItsHistoryAndTheSendersVersionAndNotSentAgain() {
        applier.apply(routeHandler, fromPeer("FAO-LIS", 4L,
                routeReplica("FAO", "LIS", RouteStatus.ACTIVE, "Route created", "Route details updated"), 1_000));

        Route stored = routeRepository.findByOriginAndDestination(new IataCode("FAO"), new IataCode("LIS")).orElseThrow();
        assertEquals(RouteStatus.ACTIVE, stored.getStatus());
        assertEquals(4L, routeRepository.findVersionFor(new IataCode("FAO"), new IataCode("LIS")));
        assertEquals(2, historyRepository.findAllByRoute("FAO", "LIS").size());
        assertEquals("airports-routes-2", replicator.homeOf(RouteReplicaHandler.TYPE, "FAO-LIS").orElseThrow());
        assertTrue(outbox.findAll().isEmpty(), "a received replica is not sent on");
    }

    @Test
    void ensureNewerReplicaReplacesTheHistoryAndOlderOneIsIgnored() {
        applier.apply(routeHandler, fromPeer("FAO-LIS", 2L,
                routeReplica("FAO", "LIS", RouteStatus.INACTIVE, "Route created", "Route deactivated"), 2_000));
        applier.apply(routeHandler, fromPeer("FAO-LIS", 1L,
                routeReplica("FAO", "LIS", RouteStatus.ACTIVE, "Route created"), 1_000));

        Route stored = routeRepository.findByOriginAndDestination(new IataCode("FAO"), new IataCode("LIS")).orElseThrow();
        assertEquals(RouteStatus.INACTIVE, stored.getStatus());
        assertEquals(2, historyRepository.findAllByRoute("FAO", "LIS").size());
    }

    @Test
    void ensureChangeOnTheSuccessorGoesBackToTheHome() {
        applier.apply(routeHandler, fromPeer("FAO-LIS", 0L, routeReplica("FAO", "LIS", RouteStatus.ACTIVE), 1_000));

        Route stored = routeRepository.findByOriginAndDestination(new IataCode("FAO"), new IataCode("LIS")).orElseThrow();
        stored.changeStatus(RouteStatus.INACTIVE);
        routeRepository.save(stored);

        assertEquals(List.of("airports-routes-2"), targetsOf("/internal/replicas/routes"));
    }

    @Test
    void ensureReceivedAirportIsStoredOnEveryReplica() {
        Airport faro = new Airport("FAO", "Faro Airport", "Faro", "Portugal", "Southern Europe",
                "Europe/Lisbon", 37.01, -7.96, List.of(new Runway("10/28", 2490, "100/280")));
        applier.apply(airportHandler, new ReplicaEnvelope("FAO", null, null,
                objectMapper.valueToTree(AirportReplica.from(faro)), 1_000L, "airports-routes-2", false));

        Airport stored = airportRepository.findByIataCode(new IataCode("FAO")).orElseThrow();
        assertEquals("Faro", stored.getCity());
        assertEquals(1, stored.getRunways().size());
        assertTrue(outbox.findAll().isEmpty());
    }

    @Test
    void ensureNewFlightCopyIsSentToTheSuccessorOnly() {
        OffsetDateTime departure = OffsetDateTime.of(2026, 3, 1, 8, 0, 0, 0, ZoneOffset.UTC);
        flightCopyRepository.save(new FlightCopy("F-REP", departure, departure.plusHours(2), FlightCopyStatus.SCHEDULED,
                "OPO", "LIS", "CS-TKA", 1));

        assertEquals(List.of("airports-routes-1"), targetsOf("/internal/replicas/flight-copies"));
    }

    @Test
    void ensureGatheredRoutesIncludeThoseHeldHereWhenPeersDoNotAnswer() {
        routeRepository.save(new Route("OPO", "CDG", 130, 1200.0, 100));

        List<Versioned<Route>> all = routesAcrossReplicas.findAll();

        assertTrue(all.stream().anyMatch(v -> v.value().getOrigin().getCode().equals("OPO")
                && v.value().getDestination().getCode().equals("CDG")));
        List<String> keys = all.stream()
                .map(v -> v.value().getOrigin().getCode() + "-" + v.value().getDestination().getCode())
                .toList();
        assertEquals(keys.stream().sorted().toList(), keys);
    }

    @Test
    void ensureDeletionLeavesATombstoneThatStopsAStaleCopyFromBringingItBack() {
        RouteReplica replica = routeReplica("FAO", "LIS", RouteStatus.ACTIVE, "Route created");
        applier.apply(routeHandler, fromPeer("FAO-LIS", 0L, replica, 1_000));
        Route stored = routeRepository.findByOriginAndDestination(new IataCode("FAO"), new IataCode("LIS")).orElseThrow();

        historyRepository.deleteAllByRoute("FAO", "LIS");
        routeRepository.delete(stored);
        boolean revived = applier.apply(routeHandler, fromPeer("FAO-LIS", 0L, replica, 1_000));

        assertFalse(revived);
        assertFalse(routeRepository.existsByOriginAndDestination(new IataCode("FAO"), new IataCode("LIS")));
        List<ReplicaEnvelope> exported = exporter.export(routeHandler, "airports-routes-2");
        assertTrue(exported.stream().anyMatch(e -> e.key().equals("FAO-LIS") && e.deleted()),
                "the tombstone is exported to the other holder");
        assertEquals(List.of("airports-routes-2"), targetsOf("/internal/replicas/routes"),
                "this replica holds the copy: the deletion goes to the route's home");
    }
}
