package aisafe.fleet.application;

import aisafe.fleet.domain.AircraftInfo;

import java.util.Optional;

/**
 * What this service needs to know about the fleet, which belongs to aircraft-maintenance. It is
 * asked through this port, never read from a local copy, so the answer is always the owner's.
 *
 * <p>When aircraft-maintenance cannot be reached, both methods throw
 * {@link aisafe.fleet.domain.FleetUnavailableException}: an unknown answer is never taken as
 * "does not exist".</p>
 */
public interface Fleet {

    /** The aircraft with this registration (contract C1), or empty if it does not exist. */
    Optional<AircraftInfo> findAircraft(String registration);

    /** Whether an aircraft model with this name exists (contract C3). */
    boolean aircraftModelExists(String modelName);
}
