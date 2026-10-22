package aisafe.flights.infrastructure.replication;

import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.IataCode;
import aisafe.flights.domain.RegistrationNumber;
import aisafe.flights.domain.ScheduledFlight;

import java.time.OffsetDateTime;

/**
 * A scheduled flight as copied to the other holder (record: held by its home replica and the next one).
 */
public record FlightReplica(
        Long id,
        OffsetDateTime departureDateTime,
        OffsetDateTime arrivalDateTime,
        FlightStatus status,
        String originIataCode,
        String destinationIataCode,
        String aircraftRegistration,
        Long version
) {
    public static FlightReplica from(ScheduledFlight flight) {
        return new FlightReplica(
                flight.getId(),
                flight.getDepartureDateTime(),
                flight.getArrivalDateTime(),
                flight.getStatus(),
                flight.getOriginCode().getCode(),
                flight.getDestinationCode().getCode(),
                flight.getAircraftRegistrationNumber().getNumber(),
                flight.getVersion()
        );
    }

    public ScheduledFlight toDomain() {
        return new ScheduledFlight(
                id,
                departureDateTime,
                arrivalDateTime,
                status,
                new IataCode(originIataCode),
                new IataCode(destinationIataCode),
                new RegistrationNumber(aircraftRegistration),
                version
        );
    }
}
