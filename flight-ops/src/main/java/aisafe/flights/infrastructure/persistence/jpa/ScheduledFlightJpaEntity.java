package aisafe.flights.infrastructure.persistence.jpa;

import aisafe.flights.domain.FlightStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "scheduled_flight")
@Getter
@Setter
public class ScheduledFlightJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "departure_date_time", nullable = false)
    private OffsetDateTime departureDateTime;

    @Column(name = "arrival_date_time", nullable = false)
    private OffsetDateTime arrivalDateTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FlightStatus status;

    @Column(name = "origin_iata_code", nullable = false, length = 3)
    private String originIataCode;

    @Column(name = "destination_iata_code", nullable = false, length = 3)
    private String destinationIataCode;

    @Column(name = "aircraft_registration", nullable = false)
    private String aircraftRegistration;

    @Version
    private Long version;

    public ScheduledFlightJpaEntity() {}

    public ScheduledFlightJpaEntity(OffsetDateTime departureDateTime, OffsetDateTime arrivalDateTime,
                                   FlightStatus status, String originIataCode, String destinationIataCode,
                                   String aircraftRegistration) {
        this.departureDateTime = departureDateTime;
        this.arrivalDateTime = arrivalDateTime;
        this.status = status;
        this.originIataCode = originIataCode;
        this.destinationIataCode = destinationIataCode;
        this.aircraftRegistration = aircraftRegistration;
    }
}
