package aisafe.airports.infrastructure.replication;

import aisafe.airports.domain.AircraftCertification;
import aisafe.airports.domain.IataCode;
import aisafe.airports.domain.ModelName;

/**
 * A certification as copied to the other replicas (catalog, like the airports it belongs to).
 */
public record AircraftCertificationReplica(String airportCode, String aircraftModelName) {

    public static String keyOf(AircraftCertification certification) {
        return certification.getAirportCode().getCode() + ":" + certification.getAircraftModelName().getName();
    }

    public static AircraftCertificationReplica from(AircraftCertification certification) {
        return new AircraftCertificationReplica(certification.getAirportCode().getCode(),
                certification.getAircraftModelName().getName());
    }

    public AircraftCertification toDomain() {
        return new AircraftCertification(new IataCode(airportCode), new ModelName(aircraftModelName));
    }
}
