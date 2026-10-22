package aisafe.flights.application;

import aisafe.flights.application.dtos.FlightUtilizationResponse;
import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.InvalidFlightDateRangeException;
import aisafe.flights.domain.ScheduledFlight;
import aisafe.shared.application.UseCase;
import aisafe.shared.application.Versioned;
import aisafe.shared.domain.PaginatedResult;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@UseCase(readOnly = true)
public class GenerateFlightUtilizationReportUseCase {

    private final FlightsAcrossReplicas flightsAcrossReplicas;

    public GenerateFlightUtilizationReportUseCase(FlightsAcrossReplicas flightsAcrossReplicas) {
        this.flightsAcrossReplicas = flightsAcrossReplicas;
    }

    public PaginatedResult<FlightUtilizationResponse> execute(OffsetDateTime startDate, OffsetDateTime endDate, Integer page, Integer size) {
        int pageNumber = page != null ? page : 0;
        int pageSize = size != null ? size : 20;

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new InvalidFlightDateRangeException("startDate cannot be after endDate");
        }

        List<ScheduledFlight> completedFlights = flightsAcrossReplicas.findAll().stream()
                .map(Versioned::value)
                .filter(f -> f.getStatus() == FlightStatus.COMPLETED)
                .filter(f -> startDate == null || !f.getDepartureDateTime().isBefore(startDate))
                .filter(f -> endDate == null || !f.getArrivalDateTime().isAfter(endDate))
                .toList();

        record RouteKey(String origin, String destination) {}

        Map<RouteKey, Long> counts = completedFlights.stream()
                .collect(Collectors.groupingBy(
                        f -> new RouteKey(f.getOriginCode().getCode(), f.getDestinationCode().getCode()),
                        Collectors.counting()
                ));

        List<FlightUtilizationResponse> allReports = counts.entrySet().stream()
                .map(e -> new FlightUtilizationResponse(
                        null,
                        e.getKey().origin(),
                        e.getKey().destination(),
                        e.getValue()
                ))
                .sorted(Comparator.comparing(FlightUtilizationResponse::count, Comparator.reverseOrder())
                        .thenComparing(FlightUtilizationResponse::origin)
                        .thenComparing(FlightUtilizationResponse::destination))
                .toList();

        return PaginatedResult.pageOf(allReports, pageNumber, pageSize);
    }
}
