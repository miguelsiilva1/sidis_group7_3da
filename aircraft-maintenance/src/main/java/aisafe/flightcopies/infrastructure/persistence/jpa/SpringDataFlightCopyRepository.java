package aisafe.flightcopies.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to the flight copies held by this replica. Reports over flights gather the
 * copies of every replica and compute in the use case, so no report queries live here.
 */
public interface SpringDataFlightCopyRepository extends JpaRepository<FlightCopyJpaEntity, String> {
}
