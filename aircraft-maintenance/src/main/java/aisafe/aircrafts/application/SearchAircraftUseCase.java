package aisafe.aircrafts.application;

import aisafe.aircrafts.domain.AircraftInvalidFieldException;
import aisafe.shared.application.UseCase;
import aisafe.aircrafts.application.dtos.SearchAircraftUseCaseResponse;
import aisafe.aircrafts.domain.AircraftStatus;
import aisafe.shared.domain.PaginatedResult;

import java.util.List;

/**
 * Searches for aircrafts based on various criteria such as model, status, and manufacturing year.
 * This use case is read-only and supports pagination and sorting.
 * The returned DTOs are lightweight and only contain fields needed for listing, not full details.
 */
@UseCase(readOnly = true)
public class SearchAircraftUseCase {

    private final AircraftAcrossReplicas aircraft;

    public SearchAircraftUseCase(AircraftAcrossReplicas aircraft) {
        this.aircraft = aircraft;
    }

    /**
     * Search for aircrafts based on pure domain criteria, over the aircrafts of every replica.
     * The page is cut only after the matches of every replica are together, sorted by registration.
     */
    public PaginatedResult<SearchAircraftUseCaseResponse> execute(String modelName, String statusStr, Integer year, String feature, int pageNumber, int pageSize) {

        AircraftStatus status = null;
        if (statusStr != null && !statusStr.isBlank()) {
            try {
                status = AircraftStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new AircraftInvalidFieldException("Invalid aircraft status: " + statusStr);
            }
        }
        AircraftStatus wantedStatus = status;
        List<SearchAircraftUseCaseResponse> matches = aircraft.findAll().stream()
                .filter(a -> modelName == null || a.getModel().getModelName().equals(modelName))
                .filter(a -> wantedStatus == null || a.getStatus() == wantedStatus)
                .filter(a -> year == null || a.getManufacturingDate().getYear() == year)
                .filter(a -> feature == null || a.getFeatures().contains(feature))
                .map(SearchAircraftUseCaseResponse::from)
                .toList();

        return PaginatedResult.pageOf(matches, pageNumber, pageSize);
    }
}
