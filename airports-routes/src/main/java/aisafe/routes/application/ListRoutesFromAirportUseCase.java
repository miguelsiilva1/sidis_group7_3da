package aisafe.routes.application;

import aisafe.shared.application.UseCase;
import aisafe.airports.domain.AirportNotFoundException;
import aisafe.airports.domain.AirportRepository;
import aisafe.airports.domain.IataCode;
import aisafe.routes.application.dtos.RouteResponse;
import aisafe.shared.domain.PaginatedResult;
import java.util.List;

/**
 * Use case responsible for listing all routes originating from a specific airport.
 *
 * <p>Airports are on every replica; routes are partitioned, so they are gathered from every
 * replica before paginating.</p>
 */
@UseCase(readOnly = true)
public class ListRoutesFromAirportUseCase {

    private final RoutesAcrossReplicas routes;
    private final AirportRepository airportRepository;

    public ListRoutesFromAirportUseCase(RoutesAcrossReplicas routes, AirportRepository airportRepository) {
        this.routes = routes;
        this.airportRepository = airportRepository;
    }

    /**
     * Retrieves a paginated result of route responses associated with the provided airport IATA code.
     *
     * @param iataCode   the IATA code of the origin airport
     * @param pageNumber the zero-based page number
     * @param pageSize   the number of results per page
     * @return a paginated result of route responses originating from the specified airport
     */
    public PaginatedResult<RouteResponse> execute(String iataCode, int pageNumber, int pageSize) {
        String originCode = iataCode.trim().toUpperCase();

        if (!airportRepository.existsByIataCode(new IataCode(originCode))) {
            throw new AirportNotFoundException(originCode);
        }

        List<RouteResponse> fromAirport = routes.findAll().stream()
                .filter(v -> v.value().getOrigin().getCode().equals(originCode))
                .map(v -> RouteResponse.from(v.value(), v.version()))
                .toList();
        return PaginatedResult.pageOf(fromAirport, pageNumber, pageSize);
    }
}
