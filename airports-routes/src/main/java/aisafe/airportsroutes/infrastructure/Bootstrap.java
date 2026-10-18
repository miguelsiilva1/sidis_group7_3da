package aisafe.airportsroutes.infrastructure;

import aisafe.airports.domain.Airport;
import aisafe.airports.domain.AirportRepository;
import aisafe.airports.domain.Runway;
import aisafe.routes.domain.Route;
import aisafe.routes.domain.RouteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Populates the database with the airport and route sample data of the monolith on startup.
 *
 * <p>Only one replica loads the sample data: the one named in {@code aisafe.bootstrap.seed-node}.
 * Airports are then copied to every replica and each route to the next replica. When that property
 * is empty (local development), the data is always loaded.</p>
 */
@Component
public class Bootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(Bootstrap.class);

    private final AirportRepository airportRepository;
    private final RouteRepository routeRepository;
    private final String nodeId;
    private final String seedNode;

    public Bootstrap(AirportRepository airportRepository,
            RouteRepository routeRepository,
            @Value("${aisafe.node.id}") String nodeId,
            @Value("${aisafe.bootstrap.seed-node:}") String seedNode) {
        this.airportRepository = airportRepository;
        this.routeRepository = routeRepository;
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

        // bootstrap for airport package
        if (airportRepository.count() == 0) {
            airportRepository.save(new Airport("LIS", "Humberto Delgado Airport", "Lisbon", "Portugal",
                    "Southern Europe", "Europe/Lisbon", 38.7742, -9.1342,
                    List.of(new Runway("03/21", 3805, "030/210"), new Runway("17/35", 2400, "170/350"))));

            airportRepository.save(new Airport("OPO", "Francisco de Sá Carneiro Airport", "Porto", "Portugal",
                    "Southern Europe", "Europe/Lisbon", 41.2481, -8.6814,
                    List.of(new Runway("17/35", 3480, "170/350"))));

            airportRepository.save(new Airport("MAD", "Adolfo Suárez Madrid-Barajas Airport", "Madrid", "Spain",
                    "Southern Europe", "Europe/Madrid", 40.4936, -3.5668,
                    List.of(new Runway("14L/32R", 4100, "140/320"), new Runway("18L/36R", 3500, "180/360"))));

            airportRepository.save(new Airport("CDG", "Charles de Gaulle Airport", "Paris", "France",
                    "Western Europe", "Europe/Paris", 49.0097, 2.5479,
                    List.of(new Runway("08L/26R", 4215, "080/260"), new Runway("09R/27L", 4200, "090/270"))));

            airportRepository.save(new Airport("LHR", "Heathrow Airport", "London", "United Kingdom",
                    "Northern Europe", "Europe/London", 51.4775, -0.4614,
                    List.of(new Runway("09L/27R", 3902, "090/270"), new Runway("09R/27L", 3658, "090/270"))));
        }

        // bootstrap for routes package
        if (routeRepository.count() == 0) {
            routeRepository.save(new Route("OPO", "LIS", 45, 280.0, 100));
            routeRepository.save(new Route("LIS", "OPO", 45, 280.0, 100));
            routeRepository.save(new Route("LIS", "MAD", 60, 640.0, 120));
            routeRepository.save(new Route("MAD", "LIS", 60, 640.0, 120));
            routeRepository.save(new Route("LIS", "CDG", 135, 1730.0, 150));
            routeRepository.save(new Route("CDG", "LIS", 135, 1730.0, 150));
            routeRepository.save(new Route("LIS", "LHR", 150, 1560.0, 150));
            routeRepository.save(new Route("LHR", "LIS", 150, 1560.0, 150));
            routeRepository.save(new Route("OPO", "MAD", 90, 850.0, 100));
            routeRepository.save(new Route("MAD", "CDG", 120, 1050.0, 120));
            routeRepository.save(new Route("CDG", "LHR", 60, 340.0, 100));
        }
    }
}
