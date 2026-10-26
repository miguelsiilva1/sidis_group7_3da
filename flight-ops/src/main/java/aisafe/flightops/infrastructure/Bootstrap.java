package aisafe.flightops.infrastructure;

import aisafe.fleet.application.Fleet;
import aisafe.fleet.domain.FlightCopy;
import aisafe.flights.application.SagaCompensationPort;
import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.IataCode;
import aisafe.flights.domain.RegistrationNumber;
import aisafe.flights.domain.ScheduledFlight;
import aisafe.flights.domain.ScheduledFlightRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Populates the database with the flight sample data of the monolith on startup.
 *
 * <p>Only one replica loads the sample data: the one named in {@code aisafe.bootstrap.seed-node}.
 * When that property is empty (local development), the data is always loaded.</p>
 */
@Component
public class Bootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(Bootstrap.class);

    private final ScheduledFlightRepository scheduledFlightRepository;
    private final Fleet fleet;
    private final SagaCompensationPort pendingCopies;
    private final String nodeId;
    private final String seedNode;

    public Bootstrap(ScheduledFlightRepository scheduledFlightRepository,
                     Fleet fleet,
                     SagaCompensationPort pendingCopies,
                     @Value("${aisafe.node.id}") String nodeId,
                     @Value("${aisafe.bootstrap.seed-node:}") String seedNode) {
        this.scheduledFlightRepository = scheduledFlightRepository;
        this.fleet = fleet;
        this.pendingCopies = pendingCopies;
        this.nodeId = nodeId;
        this.seedNode = seedNode;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedNode.isBlank() && !seedNode.equals(nodeId)) {
            log.info("Replica {} does not load the sample data (seed node: {})", nodeId, seedNode);
            return;
        }

        if (scheduledFlightRepository.count() == 0) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            // Completed flight for aircraft 1
            scheduledFlightRepository.save(new ScheduledFlight(
                    now.minusDays(2), now.minusDays(2).plusHours(2),
                    FlightStatus.COMPLETED, new IataCode("LIS"), new IataCode("OPO"), new RegistrationNumber("CS-TKA")));

            // Completed flight for aircraft 1
            scheduledFlightRepository.save(new ScheduledFlight(
                    now.minusDays(1), now.minusDays(1).plusHours(3),
                    FlightStatus.COMPLETED, new IataCode("LIS"), new IataCode("MAD"), new RegistrationNumber("CS-TKA")));

            // Completed flight for aircraft 2
            scheduledFlightRepository.save(new ScheduledFlight(
                    now.minusDays(3), now.minusDays(3).plusHours(4),
                    FlightStatus.COMPLETED, new IataCode("LIS"), new IataCode("OPO"), new RegistrationNumber("CS-TKB")));
        }

        // D6: send C4 and C5 of sample flights
        for (ScheduledFlight flight : scheduledFlightRepository.findAll()) {
            FlightCopy copy = new FlightCopy(
                    String.valueOf(flight.getId()),
                    flight.getDepartureDateTime(),
                    flight.getArrivalDateTime(),
                    flight.getStatus().name(),
                    flight.getOriginCode().getCode(),
                    flight.getDestinationCode().getCode(),
                    flight.getAircraftRegistrationNumber().getNumber(),
                    flight.getVersion() != null ? flight.getVersion() : 1L
            );
            // The other services may still be starting: a copy not delivered is retried every 5 s,
            // with the pending compensations
            try {
                fleet.sendFlightToAircraft(copy);
            } catch (Exception e) {
                log.warn("Could not send bootstrap flight copy {} to aircraft-maintenance: {}", copy.id(), e.getMessage());
                pendingCopies.recordFailedCompensation(copy, "AIRCRAFT", e.getMessage());
            }
            try {
                fleet.sendFlightToRoutes(copy);
            } catch (Exception e) {
                log.warn("Could not send bootstrap flight copy {} to airports-routes: {}", copy.id(), e.getMessage());
                pendingCopies.recordFailedCompensation(copy, "ROUTES", e.getMessage());
            }
        }
    }
}
