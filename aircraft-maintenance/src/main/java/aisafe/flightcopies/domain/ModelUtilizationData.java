package aisafe.flightcopies.domain;

/**
 * Utilization of an aircraft model: flight hours or number of completed flights.
 */
public record ModelUtilizationData(String modelName, Long utilizationValue) {
}
