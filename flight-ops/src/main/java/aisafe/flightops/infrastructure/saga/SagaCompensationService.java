package aisafe.flightops.infrastructure.saga;

import aisafe.fleet.application.Fleet;
import aisafe.fleet.domain.FleetUnavailableException;
import aisafe.fleet.domain.FlightCopy;
import aisafe.flights.application.SagaCompensationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Persists compensations that could not be delivered over the network and retries them periodically.
 */
@Service
public class SagaCompensationService implements SagaCompensationPort {

    private static final Logger log = LoggerFactory.getLogger(SagaCompensationService.class);

    private final SagaCompensationRepository repository;
    private final Fleet fleet;

    public SagaCompensationService(SagaCompensationRepository repository, Fleet fleet) {
        this.repository = repository;
        this.fleet = fleet;
    }

    @Override
    @Transactional
    public void recordFailedCompensation(FlightCopy copy, String targetService, String error) {
        String truncatedError = (error != null && error.length() > 500) ? error.substring(0, 500) : error;
        SagaCompensationJpaEntity entity = new SagaCompensationJpaEntity(
                copy.id(),
                targetService,
                copy.departureDateTime(),
                copy.arrivalDateTime(),
                copy.status(),
                copy.originIataCode(),
                copy.destinationIataCode(),
                copy.aircraftRegistration(),
                copy.version(),
                1,
                OffsetDateTime.now(ZoneOffset.UTC),
                truncatedError
        );
        repository.save(entity);
        log.warn("Saved failed compensation for flight {} targeting {} for later retry: {}",
                copy.id(), targetService, truncatedError);
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    @Transactional
    public void retryCompensations() {
        List<SagaCompensationJpaEntity> compensations = repository.findAll();
        if (compensations.isEmpty()) {
            return;
        }
        log.info("Retrying {} pending saga compensations...", compensations.size());
        for (SagaCompensationJpaEntity comp : compensations) {
            FlightCopy copy = new FlightCopy(
                    comp.getFlightId(),
                    comp.getDepartureDateTime(),
                    comp.getArrivalDateTime(),
                    comp.getStatus(),
                    comp.getOriginIataCode(),
                    comp.getDestinationIataCode(),
                    comp.getAircraftRegistration(),
                    comp.getVersion()
            );
            try {
                if ("AIRCRAFT".equalsIgnoreCase(comp.getTargetService())) {
                    fleet.sendFlightToAircraft(copy);
                } else if ("ROUTES".equalsIgnoreCase(comp.getTargetService())) {
                    fleet.sendFlightToRoutes(copy);
                }
                repository.delete(comp);
                log.info("Saga compensation for flight {} to {} succeeded and was removed.",
                        comp.getFlightId(), comp.getTargetService());
            } catch (FleetUnavailableException | RestClientException e) {
                log.warn("Saga compensation for flight {} to {} failed again: {}",
                        comp.getFlightId(), comp.getTargetService(), e.getMessage());
                comp.setAttempts(comp.getAttempts() + 1);
                String err = e.getMessage();
                comp.setLastError(err != null && err.length() > 500 ? err.substring(0, 500) : err);
                repository.save(comp);
            } catch (Exception e) {
                log.warn("Saga compensation for flight {} to {} failed with non-retryable error: {}. Discarding.",
                        comp.getFlightId(), comp.getTargetService(), e.getMessage());
                repository.delete(comp);
            }
        }
    }
}
