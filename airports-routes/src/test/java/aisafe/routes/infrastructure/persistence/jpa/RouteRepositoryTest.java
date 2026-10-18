package aisafe.routes.infrastructure.persistence.jpa;

import aisafe.airports.domain.IataCode;
import aisafe.routes.domain.Route;
import aisafe.routes.domain.RouteRepository;
import aisafe.routes.domain.RouteStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("jpa")
@Transactional
class RouteRepositoryTest {

    @Autowired
    private SpringDataRouteRepository springRepo;

    @Autowired
    private RouteRepository routeRepository;

    @Test
    void ensureFindByOriginAndDestinationWorksWithIataCode() {
        springRepo.deleteAll();
        IataCode origin = new IataCode("LIS");
        IataCode destination = new IataCode("OPO");
        Route route = new Route(origin.getCode(), destination.getCode(), 45, 300.0, 150);
        routeRepository.save(route);

        Optional<Route> found = routeRepository.findByOriginAndDestination(origin, destination);

        assertTrue(found.isPresent());
        assertEquals("LIS", found.get().getOrigin().getCode());
        assertEquals("OPO", found.get().getDestination().getCode());
    }

    @Test
    void ensureFindByOriginAndDestinationReturnsEmptyWhenNotFound() {
        springRepo.deleteAll();
        IataCode origin = new IataCode("LIS");
        IataCode destination = new IataCode("MAD");

        Optional<Route> found = routeRepository.findByOriginAndDestination(origin, destination);

        assertFalse(found.isPresent());
    }

    @Test
    void ensureRouteRepositoryOperationsWork() {
        springRepo.deleteAll();

        // 1. Save new
        Route route = new Route("LIS", "OPO", 45, 300.0, 150);
        routeRepository.save(route);
        assertEquals(1, routeRepository.count());

        // 2. Save existing (updating)
        route.updateRoute(50, 350.0, 160);
        routeRepository.save(route);
        assertEquals(1, routeRepository.count());
        Route saved = routeRepository.findByOriginAndDestination(new IataCode("LIS"), new IataCode("OPO")).get();
        assertEquals(50, saved.getEstimatedFlightTime());

        // 3. Find Version
        Long version = routeRepository.findVersionFor(new IataCode("LIS"), new IataCode("OPO"));
        assertNotNull(version);

        // 4. Exists
        assertTrue(routeRepository.existsByOriginAndDestination(new IataCode("LIS"), new IataCode("OPO")));
        assertFalse(routeRepository.existsByOriginAndDestination(new IataCode("LIS"), new IataCode("MAD")));

        // 5. Find All
        List<Route> all = routeRepository.findAll();
        assertEquals(1, all.size());

        // 6. Align the version with the one of the replica that sent it
        springRepo.alignVersion("LIS", "OPO", 42L);
        assertEquals(42L, routeRepository.findVersionFor(new IataCode("LIS"), new IataCode("OPO")));

        // 7. Delete
        routeRepository.delete(route);
        assertEquals(0, routeRepository.count());
    }
}
