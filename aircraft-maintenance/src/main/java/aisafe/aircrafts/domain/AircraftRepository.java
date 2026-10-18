package aisafe.aircrafts.domain;

import aisafe.shared.domain.BaseRepository;

import java.util.Optional;

/**
 * Repository interface for managing `Aircraft` entities.
 */
public interface AircraftRepository extends BaseRepository<Aircraft> {
    Optional<Aircraft> findByRegistrationNumber(RegistrationNumber registrationNumber);
    boolean existsByRegistrationNumber(RegistrationNumber registrationNumber);
    Long findVersionFor(RegistrationNumber reg);
}