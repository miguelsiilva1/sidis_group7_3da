package aisafe.airports.infrastructure.persistence.jpa;

import aisafe.airports.domain.AircraftCertification;
import aisafe.airports.domain.IataCode;
import aisafe.airports.domain.ModelName;

public class AircraftCertificationMapper {

    private AircraftCertificationMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static AircraftCertification toDomain(AircraftCertificationJpaEntity entity) {
        if (entity == null) return null;
        IataCode airportCode = new IataCode(entity.getAirport().getIataCode().getCode());
        return new AircraftCertification(airportCode, new ModelName(entity.getAircraftModelName()));
    }
}
