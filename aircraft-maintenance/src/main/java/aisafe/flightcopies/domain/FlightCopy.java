package aisafe.flightcopies.domain;

import aisafe.aircrafts.domain.RegistrationNumber;
import aisafe.shared.domain.DomainException;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Local copy of a flight owned by flight-ops.
 *
 * <p>flight-ops sends a copy of every flight it creates or changes (a command, not an event).
 * This service keeps the copies to answer its own reports (operational hours, utilization,
 * maintenance due) without calling flight-ops. Each copy carries the version of the flight in
 * flight-ops, so an older copy never replaces a newer one.</p>
 */
public class FlightCopy {
    private final String id;
    private final OffsetDateTime departureDateTime;
    private final OffsetDateTime arrivalDateTime;
    private final FlightCopyStatus status;
    private final String originCode;
    private final String destinationCode;
    private final RegistrationNumber aircraftRegistrationNumber;
    private final long version;

    public FlightCopy(String id, OffsetDateTime departureDateTime, OffsetDateTime arrivalDateTime,
                      FlightCopyStatus status, String originCode, String destinationCode,
                      RegistrationNumber aircraftRegistrationNumber, long version) {
        if (id == null || id.isBlank()) {
            throw new DomainException("Flight id is required.");
        }
        if (departureDateTime == null || arrivalDateTime == null) {
            throw new DomainException("Departure and arrival date/times are required.");
        }
        if (!arrivalDateTime.isAfter(departureDateTime)) {
            throw new DomainException("Arrival must be after departure.");
        }
        if (status == null) {
            throw new DomainException("Flight status is required.");
        }
        if (originCode == null || originCode.isBlank() || destinationCode == null || destinationCode.isBlank()) {
            throw new DomainException("Origin and destination are required.");
        }
        if (aircraftRegistrationNumber == null) {
            throw new DomainException("Aircraft registration number is required.");
        }
        if (version < 0) {
            throw new DomainException("Version must not be negative.");
        }
        this.id = id;
        this.departureDateTime = departureDateTime;
        this.arrivalDateTime = arrivalDateTime;
        this.status = status;
        this.originCode = originCode;
        this.destinationCode = destinationCode;
        this.aircraftRegistrationNumber = aircraftRegistrationNumber;
        this.version = version;
    }

    public String getId() { return id; }
    public OffsetDateTime getDepartureDateTime() { return departureDateTime; }
    public OffsetDateTime getArrivalDateTime() { return arrivalDateTime; }
    public FlightCopyStatus getStatus() { return status; }
    public String getOriginCode() { return originCode; }
    public String getDestinationCode() { return destinationCode; }
    public RegistrationNumber getAircraftRegistrationNumber() { return aircraftRegistrationNumber; }
    public long getVersion() { return version; }

    public Duration getDuration() {
        return Duration.between(departureDateTime, arrivalDateTime);
    }

    /**
     * A copy replaces the stored one only if it has a higher version. Receiving the same copy
     * twice (a retry) or an older one (arriving late) changes nothing.
     */
    public boolean supersedes(FlightCopy stored) {
        return version > stored.version;
    }

    public boolean isCanceled() {
        return status == FlightCopyStatus.CANCELED;
    }
}
