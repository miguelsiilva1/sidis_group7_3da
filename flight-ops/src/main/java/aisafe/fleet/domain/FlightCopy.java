package aisafe.fleet.domain;

import java.time.OffsetDateTime;

/**
 * Copy of a flight sent by flight-ops to aircraft-maintenance (contract C4)
 * and airports-routes (contract C5).
 */
public record FlightCopy(
        String id,
        OffsetDateTime departureDateTime,
        OffsetDateTime arrivalDateTime,
        String status,
        String originIataCode,
        String destinationIataCode,
        String aircraftRegistration,
        long version
) {
}
