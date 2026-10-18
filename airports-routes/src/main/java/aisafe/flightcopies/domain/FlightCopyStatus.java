package aisafe.flightcopies.domain;

/**
 * Status of a flight, as sent by flight-ops. The values mirror flight-ops' own flight status.
 */
public enum FlightCopyStatus {
    SCHEDULED,
    DELAYED,
    IN_FLIGHT,
    COMPLETED,
    CANCELED
}
