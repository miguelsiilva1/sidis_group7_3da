package aisafe.aircrafts.application;

import aisafe.flightcopies.domain.FlightCopyStatus;
import aisafe.flightcopies.domain.FlightCopy;
import aisafe.flightcopies.application.FlightCopiesAcrossReplicas;
import aisafe.aircrafts.application.dtos.TopUtilizedModelResponse;
import aisafe.aircrafts.domain.InvalidUtilizationCriteriaException;
import aisafe.flightcopies.domain.ModelUtilizationData;
import aisafe.shared.application.UseCase;

import java.util.function.ToLongFunction;
import java.util.Map;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Gets the Top 5 most utilized aircraft models based on total flight hours or number of assignments.
 */
@UseCase(readOnly = true)
public class GetTopUtilizedModelsUseCase {

    private static final int TOP = 5;

    private final FlightCopiesAcrossReplicas flights;
    private final AircraftAcrossReplicas aircraft;

    public GetTopUtilizedModelsUseCase(FlightCopiesAcrossReplicas flights, AircraftAcrossReplicas aircraft) {
        this.flights = flights;
        this.aircraft = aircraft;
    }

    /**
     * Completed flights of every replica, grouped by the model of their aircraft (flights of an
     * aircraft that no longer exists are left out). HOURS: whole flight hours; ASSIGNMENTS:
     * number of flights.
     */
    public List<TopUtilizedModelResponse> execute(String criteria) {
        ToLongFunction<List<FlightCopy>> value;
        if ("HOURS".equalsIgnoreCase(criteria)) {
            value = modelFlights -> modelFlights.stream().mapToLong(f -> f.getDuration().toSeconds()).sum() / 3600;
        } else if ("ASSIGNMENTS".equalsIgnoreCase(criteria)) {
            value = List::size;
        } else {
            throw new InvalidUtilizationCriteriaException("Invalid criteria. Must be 'HOURS' or 'ASSIGNMENTS'.");
        }

        Map<String, String> modelOf = aircraft.findAll().stream()
                .collect(Collectors.toMap(a -> a.getRegistrationNumber().getNumber(), a -> a.getModel().getModelName()));
        Map<String, List<FlightCopy>> flightsByModel = flights.findAll().stream()
                .filter(f -> f.getStatus() == FlightCopyStatus.COMPLETED)
                .filter(f -> modelOf.containsKey(f.getAircraftRegistrationNumber().getNumber()))
                .collect(Collectors.groupingBy(f -> modelOf.get(f.getAircraftRegistrationNumber().getNumber())));

        return flightsByModel.entrySet().stream()
                .map(e -> new ModelUtilizationData(e.getKey(), value.applyAsLong(e.getValue())))
                .sorted(Comparator.comparing(ModelUtilizationData::utilizationValue).reversed()
                        .thenComparing(ModelUtilizationData::modelName))
                .limit(TOP)
                .map(d -> new TopUtilizedModelResponse(d.modelName(), d.utilizationValue()))
                .collect(Collectors.toList());
    }
}
