package aisafe.fleet.application;

import aisafe.fleet.domain.AircraftInfo;
import aisafe.fleet.domain.FlightCopy;
import aisafe.fleet.domain.RouteInfo;

import java.util.Optional;

/**
 * Port for communicating with aircraft-maintenance and airports-routes.
 */
public interface Fleet {

    /** The aircraft with this registration (contract C1), or empty if it does not exist. */
    Optional<AircraftInfo> findAircraft(String registration);

    /** Whether an aircraft model with this name exists (contract C3). */
    boolean aircraftModelExists(String modelName);

    /** The route between origin and destination (contract C2), or empty if it does not exist. */
    Optional<RouteInfo> findRoute(String origin, String destination);

    /** Sends the flight copy to aircraft-maintenance (contract C4). */
    void sendFlightToAircraft(FlightCopy copy);

    /** Sends the flight copy to airports-routes (contract C5). */
    void sendFlightToRoutes(FlightCopy copy);

    default void sendFlightCopyAircraft(FlightCopy copy) {
        sendFlightToAircraft(copy);
    }

    default void sendFlightCopyRoutes(FlightCopy copy) {
        sendFlightToRoutes(copy);
    }
}
