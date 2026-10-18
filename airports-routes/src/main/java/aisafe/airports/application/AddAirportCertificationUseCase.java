package aisafe.airports.application;

import aisafe.shared.domain.DuplicateResourceException;
import aisafe.shared.application.UseCase;
import aisafe.fleet.application.Fleet;
import aisafe.fleet.domain.AircraftModelNotFoundException;
import aisafe.airports.application.dtos.AddCertificationRequest;
import aisafe.airports.application.dtos.AircraftCertificationResponse;
import aisafe.airports.domain.Airport;
import aisafe.airports.domain.AircraftCertification;
import aisafe.airports.domain.AircraftCertificationRepository;
import aisafe.airports.domain.AirportNotFoundException;
import aisafe.airports.domain.AirportRepository;
import aisafe.airports.domain.IataCode;
import aisafe.airports.domain.ModelName;

/**
 * Certifies an aircraft model for an airport (US106a). Aircraft models belong to
 * aircraft-maintenance, which is asked whether the model exists (contract C3).
 */
@UseCase
public class AddAirportCertificationUseCase {
    private final AirportRepository airportRepository;
    private final AircraftCertificationRepository certificationRepository;
    private final Fleet fleet;

    public AddAirportCertificationUseCase(AirportRepository airportRepository,
                                          AircraftCertificationRepository certificationRepository,
                                          Fleet fleet) {
        this.airportRepository = airportRepository;
        this.certificationRepository = certificationRepository;
        this.fleet = fleet;
    }

    public AircraftCertificationResponse execute(String iataCodeStr, AddCertificationRequest request) {
        Airport airport = airportRepository.findByIataCode(new IataCode(iataCodeStr))
                .orElseThrow(() -> new AirportNotFoundException(iataCodeStr));

        ModelName modelName = new ModelName(request.aircraftModelName());

        if (!fleet.aircraftModelExists(modelName.getName())) {
            throw new AircraftModelNotFoundException(modelName.getName());
        }

        if (certificationRepository.existsByAirportCodeAndAircraftModelName(airport.getIataCode(), modelName)) {
            throw new DuplicateResourceException("Aircraft model '" + modelName.getName() + "' is already certified for airport " + iataCodeStr + ".");
        }

        AircraftCertification certification = new AircraftCertification(airport.getIataCode(), modelName);
        certificationRepository.save(certification);

        return AircraftCertificationResponse.from(certification);
    }
}
