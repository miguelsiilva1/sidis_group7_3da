package aisafe.aircrafts.application;

import aisafe.shared.application.UseCase;
import aisafe.aircrafts.application.dtos.ListAircraftRequest;
import aisafe.aircrafts.application.dtos.AircraftResponse;
import aisafe.shared.domain.PaginatedResult;

import java.util.List;

/**
 * Returns all stored aircrafts for the aircraft management screens and APIs.
 * This use case is read-only and supports pagination and sorting.
 * The returned DTOs are lightweight and only contain fields needed for listing, not full details.
 *
 * <p>Aircrafts are partitioned between the replicas of this service, so the list is gathered
 * from this replica and from every peer, merged without duplicates, sorted by registration
 * number and only then paginated. Paginating on each replica first would return wrong pages.</p>
 */
@UseCase(readOnly = true)
public class ListAircraftUseCase {
    private final AircraftAcrossReplicas aircraft;

    public ListAircraftUseCase(AircraftAcrossReplicas aircraft) {
        this.aircraft = aircraft;
    }

    /**
     * Return all aircrafts as lightweight DTOs used by the API/UI.
     * @param request the current page index (starting from 0) and the number of items per page
     * @return one page of the aircrafts of every replica
     */
    public PaginatedResult<AircraftResponse> execute(ListAircraftRequest request) {
        List<AircraftResponse> all = aircraft.findAll().stream()
                .map(a -> AircraftResponse.from(a, null))
                .toList();
        return PaginatedResult.pageOf(all, request.pageNumber(), request.pageSize());
    }
}
