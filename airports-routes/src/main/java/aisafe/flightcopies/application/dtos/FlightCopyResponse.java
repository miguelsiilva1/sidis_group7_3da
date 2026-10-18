package aisafe.flightcopies.application.dtos;

import aisafe.flightcopies.domain.FlightCopy;

import java.time.OffsetDateTime;

/**
 * The copy this replica now holds. It may be newer than the one just sent, if that one arrived late.
 */
public record FlightCopyResponse(
        String id,
        OffsetDateTime departureDateTime,
        OffsetDateTime arrivalDateTime,
        String status,
        String originIataCode,
        String destinationIataCode,
        String aircraftRegistration,
        long version
) {
    public static FlightCopyResponse from(FlightCopy copy) {
        return new FlightCopyResponse(
                copy.getId(),
                copy.getDepartureDateTime(),
                copy.getArrivalDateTime(),
                copy.getStatus().name(),
                copy.getOriginCode(),
                copy.getDestinationCode(),
                copy.getAircraftRegistration(),
                copy.getVersion()
        );
    }
}
