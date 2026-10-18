package aisafe.routes.application;

import aisafe.shared.application.UseCase;
import aisafe.airports.domain.IataCode;
import aisafe.routes.application.dtos.RouteResponse;
import aisafe.routes.application.dtos.SearchRoutesRequest;
import aisafe.shared.domain.PaginatedResult;
import java.util.List;

/**
 * Use case responsible for searching routes based on origin and destination criteria.
 *
 * <p>Routes are partitioned between the replicas of this service, so the routes of every replica
 * are gathered, filtered, sorted by origin and destination and only then paginated.</p>
 */
@UseCase(readOnly = true)
public class SearchRoutesUseCase {

    private final RoutesAcrossReplicas routes;

    public SearchRoutesUseCase(RoutesAcrossReplicas routes) {
        this.routes = routes;
    }

    /**
     * Retrieves a paginated result of route responses matching the provided origin and destination.
     *
     * @param request the origin and destination IATA codes (each optional), the zero-based page
     *                number and the number of results per page
     * @return a paginated result of route responses matching the search criteria
     */
    public PaginatedResult<RouteResponse> execute(SearchRoutesRequest request) {
        // Validates the codes, as before (an invalid code is a bad request, not an empty result)
        String origin = request.origin() == null ? null : new IataCode(request.origin()).getCode();
        String destination = request.destination() == null ? null : new IataCode(request.destination()).getCode();
        List<RouteResponse> matching = routes.findAll().stream()
                .filter(v -> origin == null || v.value().getOrigin().getCode().equals(origin))
                .filter(v -> destination == null || v.value().getDestination().getCode().equals(destination))
                .map(v -> RouteResponse.from(v.value(), v.version()))
                .toList();
        return PaginatedResult.pageOf(matching, request.pageNumber(), request.pageSize());
    }
}
