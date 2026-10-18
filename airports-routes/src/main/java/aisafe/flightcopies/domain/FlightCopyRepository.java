package aisafe.flightcopies.domain;

import aisafe.shared.domain.BaseRepository;

import java.util.Optional;

/**
 * Copies of the flights sent by flight-ops held by this replica. Reports read the copies of every
 * replica through {@code FlightCopiesAcrossReplicas}.
 */
public interface FlightCopyRepository extends BaseRepository<FlightCopy> {
    Optional<FlightCopy> findById(String id);
}
