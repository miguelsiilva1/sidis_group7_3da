package aisafe.flights.application;

import aisafe.fleet.domain.FlightCopy;

/**
 * Port for saving and retrying failed Saga compensations.
 */
public interface SagaCompensationPort {
    void recordFailedCompensation(FlightCopy copy, String targetService, String error);
}
