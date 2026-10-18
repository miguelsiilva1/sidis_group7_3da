package aisafe.flightcopies.infrastructure.replication;

import aisafe.flightcopies.domain.FlightCopy;
import aisafe.flightcopies.domain.FlightCopyStatus;

import java.time.OffsetDateTime;

/**
 * A flight copy as copied to the other holder (record: held by the replica that received it from
 * flight-ops and the next one). The version is the flight's version in flight-ops.
 */
public record FlightCopyReplica(
        String id,
        OffsetDateTime departureDateTime,
        OffsetDateTime arrivalDateTime,
        FlightCopyStatus status,
        String originCode,
        String destinationCode,
        String aircraftRegistration,
        long version
) {
    public static FlightCopyReplica from(FlightCopy copy) {
        return new FlightCopyReplica(copy.getId(), copy.getDepartureDateTime(), copy.getArrivalDateTime(),
                copy.getStatus(), copy.getOriginCode(), copy.getDestinationCode(),
                copy.getAircraftRegistration(), copy.getVersion());
    }

    public FlightCopy toDomain() {
        return new FlightCopy(id, departureDateTime, arrivalDateTime, status, originCode, destinationCode,
                aircraftRegistration, version);
    }
}
