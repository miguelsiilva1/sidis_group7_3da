package aisafe.airports.application;

import aisafe.shared.application.UseCase;
import aisafe.airports.domain.Airport;
import aisafe.airports.domain.AirportNotFoundException;
import aisafe.airports.domain.AirportRepository;
import aisafe.airports.domain.IataCode;
import aisafe.routes.application.RoutesAcrossReplicas;
import aisafe.shared.domain.ResourceInUseException;

/**
 * Deletes an airport that no route of any replica refers to.
 */
@UseCase
public class DeleteAirportUseCase {
    private final AirportRepository airportRepository;
    private final RoutesAcrossReplicas routes;

    public DeleteAirportUseCase(AirportRepository airportRepository, RoutesAcrossReplicas routes) {
        this.airportRepository = airportRepository;
        this.routes = routes;
    }

    public void execute(String iataCode) {
        Airport airport = airportRepository.findByIataCode(new IataCode(iataCode))
                .orElseThrow(() -> new AirportNotFoundException(iataCode));
        IataCode code = new IataCode(iataCode);
        if (routes.findAll().stream().anyMatch(v -> v.value().getOrigin().equals(code) || v.value().getDestination().equals(code))) {
            throw new ResourceInUseException("Airport " + iataCode + " cannot be deleted because it is referenced by existing routes.");
        }
        airportRepository.delete(airport);
    }
}
