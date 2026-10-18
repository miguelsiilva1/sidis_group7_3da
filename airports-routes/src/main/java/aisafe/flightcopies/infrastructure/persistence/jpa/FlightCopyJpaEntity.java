package aisafe.flightcopies.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Persistence model of a flight copy. The id and the version come from flight-ops.
 */
@Entity
@Table(name = "flight_copies", indexes = @Index(name = "idx_flight_copies_route", columnList = "originCode, destinationCode"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlightCopyJpaEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private OffsetDateTime departureDateTime;

    @Column(nullable = false)
    private OffsetDateTime arrivalDateTime;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false, length = 3)
    private String originCode;

    @Column(nullable = false, length = 3)
    private String destinationCode;

    @Column(nullable = false)
    private String aircraftRegistration;

    @Column(nullable = false)
    private long version;

    public FlightCopyJpaEntity(String id, OffsetDateTime departureDateTime, OffsetDateTime arrivalDateTime,
                               String status, String originCode, String destinationCode,
                               String aircraftRegistration, long version) {
        this.id = id;
        this.departureDateTime = departureDateTime;
        this.arrivalDateTime = arrivalDateTime;
        this.status = status;
        this.originCode = originCode;
        this.destinationCode = destinationCode;
        this.aircraftRegistration = aircraftRegistration;
        this.version = version;
    }
}
