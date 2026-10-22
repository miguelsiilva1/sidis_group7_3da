package aisafe.flights.domain;

import aisafe.shared.domain.BaseRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Scheduled flights held by this replica. Listings, reports and checks over every replica read
 * through {@code FlightsAcrossReplicas}.
 */
public interface ScheduledFlightRepository extends BaseRepository<ScheduledFlight> {
    Optional<ScheduledFlight> findById(Long id);
    Optional<ScheduledFlight> findByAircraftRegistrationAndDepartureDateTime(RegistrationNumber registration, OffsetDateTime departureDateTime);
    Long findVersionFor(Long id);
}
