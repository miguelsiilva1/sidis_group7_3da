package aisafe.flightcopies.infrastructure.persistence.jpa;

import aisafe.flightcopies.domain.FlightCopy;
import aisafe.flightcopies.domain.FlightCopyStatus;

/**
 * Maps between the flight copy domain object and its persistence model.
 */
public final class FlightCopyMapper {

    private FlightCopyMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static FlightCopyJpaEntity toJpa(FlightCopy copy) {
        return new FlightCopyJpaEntity(
                copy.getId(),
                copy.getDepartureDateTime(),
                copy.getArrivalDateTime(),
                copy.getStatus().name(),
                copy.getOriginCode(),
                copy.getDestinationCode(),
                copy.getAircraftRegistration(),
                copy.getVersion());
    }

    public static FlightCopy toDomain(FlightCopyJpaEntity entity) {
        return new FlightCopy(
                entity.getId(),
                entity.getDepartureDateTime(),
                entity.getArrivalDateTime(),
                FlightCopyStatus.valueOf(entity.getStatus()),
                entity.getOriginCode(),
                entity.getDestinationCode(),
                entity.getAircraftRegistration(),
                entity.getVersion());
    }
}
