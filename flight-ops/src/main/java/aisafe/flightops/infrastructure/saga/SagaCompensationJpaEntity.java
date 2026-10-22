package aisafe.flightops.infrastructure.saga;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "saga_compensation")
@Getter
@Setter
@NoArgsConstructor
public class SagaCompensationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flight_id", nullable = false)
    private String flightId;

    @Column(name = "target_service", nullable = false, length = 50)
    private String targetService;

    @Column(name = "departure_date_time", nullable = false)
    private OffsetDateTime departureDateTime;

    @Column(name = "arrival_date_time", nullable = false)
    private OffsetDateTime arrivalDateTime;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "origin_iata_code", nullable = false, length = 3)
    private String originIataCode;

    @Column(name = "destination_iata_code", nullable = false, length = 3)
    private String destinationIataCode;

    @Column(name = "aircraft_registration", nullable = false)
    private String aircraftRegistration;

    @Column(name = "version", nullable = false)
    private Long version;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    public SagaCompensationJpaEntity(String flightId, String targetService, OffsetDateTime departureDateTime,
                                     OffsetDateTime arrivalDateTime, String status, String originIataCode,
                                     String destinationIataCode, String aircraftRegistration, Long version,
                                     int attempts, OffsetDateTime createdAt, String lastError) {
        this.flightId = flightId;
        this.targetService = targetService;
        this.departureDateTime = departureDateTime;
        this.arrivalDateTime = arrivalDateTime;
        this.status = status;
        this.originIataCode = originIataCode;
        this.destinationIataCode = destinationIataCode;
        this.aircraftRegistration = aircraftRegistration;
        this.version = version;
        this.attempts = attempts;
        this.createdAt = createdAt;
        this.lastError = lastError;
    }
}
