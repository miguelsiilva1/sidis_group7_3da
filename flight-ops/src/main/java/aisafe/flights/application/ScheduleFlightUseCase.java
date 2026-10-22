package aisafe.flights.application;

import aisafe.fleet.application.Fleet;
import aisafe.fleet.domain.AircraftConflictException;
import aisafe.fleet.domain.AircraftInfo;
import aisafe.fleet.domain.AircraftNotFoundException;
import aisafe.fleet.domain.FleetConflictException;
import aisafe.fleet.domain.FleetUnavailableException;
import aisafe.fleet.domain.FlightCopy;
import aisafe.fleet.domain.RouteConflictException;
import aisafe.fleet.domain.RouteInfo;
import aisafe.fleet.domain.RouteNotFoundException;
import aisafe.flights.application.dtos.FlightResponse;
import aisafe.flights.application.dtos.ScheduleFlightRequest;
import aisafe.flights.domain.AircraftIncompatibilityException;
import aisafe.flights.domain.AircraftUnavailableException;
import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.IataCode;
import aisafe.flights.domain.RegistrationNumber;
import aisafe.flights.domain.ScheduledFlight;
import aisafe.flights.domain.ScheduledFlightRepository;
import aisafe.shared.application.UseCase;
import aisafe.shared.application.Versioned;
import lombok.RequiredArgsConstructor;
import org.springframework.web.client.RestClientException;

@UseCase
@RequiredArgsConstructor
public class ScheduleFlightUseCase {

    private final ScheduledFlightRepository scheduledFlightRepository;
    private final FlightsAcrossReplicas flightsAcrossReplicas;
    private final Fleet fleet;
    private final SagaCompensationPort sagaCompensationPort;

    public FlightResponse execute(ScheduleFlightRequest request) {
        String aircraftId = request.aircraftId().trim().toUpperCase();
        String origin = request.originIataCode().trim().toUpperCase();
        String destination = request.destinationIataCode().trim().toUpperCase();

        // T1: C2: rota existe, ativa, capacidade e alcance mínimos, distância
        RouteInfo route = fleet.findRoute(origin, destination)
                .orElseThrow(() -> new RouteNotFoundException(origin, destination));

        if (route.status() != null && !"ACTIVE".equalsIgnoreCase(route.status())) {
            throw new RouteConflictException("Route " + origin + " -> " + destination + " is not active.");
        }

        // T2: C1: avião existe, estado, lugares, alcance (>= distância da rota)
        AircraftInfo aircraft = fleet.findAircraft(aircraftId)
                .orElseThrow(() -> new AircraftNotFoundException(aircraftId));

        if ("UNDER_MAINTENANCE".equalsIgnoreCase(aircraft.status()) || "INACTIVE".equalsIgnoreCase(aircraft.status())) {
            throw new AircraftUnavailableException(aircraftId);
        }
        if (aircraft.seatCapacity() != null && route.minimumCapacity() != null
                && aircraft.seatCapacity() < route.minimumCapacity()) {
            throw new AircraftIncompatibilityException("Aircraft capacity (" + aircraft.seatCapacity()
                    + ") is less than the route minimum capacity (" + route.minimumCapacity() + ").");
        }
        if (aircraft.range() != null && route.minimumRange() != null
                && aircraft.range() < route.minimumRange()) {
            throw new AircraftIncompatibilityException("Aircraft range (" + aircraft.range()
                    + ") is less than the route minimum range (" + route.minimumRange() + ").");
        }
        if (route.distanceKm() != null && aircraft.range() != null
                && route.distanceKm() > aircraft.range()) {
            throw new AircraftIncompatibilityException("Route distance exceeds aircraft maximum range.");
        }

        // T3: Verificar sobreposições (em todas as réplicas); gravar o voo como PENDENTE
        boolean overlapping = flightsAcrossReplicas.findAll().stream()
                .map(Versioned::value)
                .filter(f -> f.getAircraftRegistrationNumber().getNumber().equalsIgnoreCase(aircraftId))
                .filter(f -> f.getStatus() != FlightStatus.CANCELED)
                .anyMatch(f -> f.getDepartureDateTime().isBefore(request.arrivalDateTime())
                            && f.getArrivalDateTime().isAfter(request.departureDateTime()));
        if (overlapping) {
            throw new AircraftUnavailableException(aircraftId);
        }

        RegistrationNumber regNum = new RegistrationNumber(aircraftId);
        ScheduledFlight scheduledFlight = new ScheduledFlight(
                request.departureDateTime(),
                request.arrivalDateTime(),
                FlightStatus.PENDING,
                new IataCode(origin),
                new IataCode(destination),
                regNum
        );
        ScheduledFlight saved = scheduledFlightRepository.save(scheduledFlight);
        String flightId = String.valueOf(saved.getId());

        FlightCopy copy = new FlightCopy(
                flightId,
                saved.getDepartureDateTime(),
                saved.getArrivalDateTime(),
                "SCHEDULED",
                origin,
                destination,
                aircraftId,
                saved.getVersion() != null ? saved.getVersion() : 1L
        );

        // T4: C4 (reserva o avião)
        try {
            fleet.sendFlightToAircraft(copy);
        } catch (FleetConflictException e) {
            saved.cancel();
            scheduledFlightRepository.save(saved);
            throw new AircraftUnavailableException(aircraftId);
        } catch (FleetUnavailableException e) {
            saved.cancel();
            scheduledFlightRepository.save(saved);
            throw e;
        }

        // T5: C5
        try {
            fleet.sendFlightToRoutes(copy);
        } catch (FleetConflictException e) {
            compensateAircraftMaintenance(saved, copy);
            saved.cancel();
            scheduledFlightRepository.save(saved);
            throw new RouteConflictException("Route " + origin + " -> " + destination + " is unavailable or inactive.");
        } catch (FleetUnavailableException e) {
            compensateAircraftMaintenance(saved, copy);
            saved.cancel();
            scheduledFlightRepository.save(saved);
            throw e;
        }

        // T6: Voo AGENDADO; 201 ao cliente
        saved.markScheduled();
        saved = scheduledFlightRepository.save(saved);
        return FlightResponse.from(saved);
    }

    private void compensateAircraftMaintenance(ScheduledFlight saved, FlightCopy copy) {
        long compVersion = (saved.getVersion() != null ? saved.getVersion() : 1L) + 1;
        FlightCopy canceledCopy = new FlightCopy(
                copy.id(),
                copy.departureDateTime(),
                copy.arrivalDateTime(),
                "CANCELED",
                copy.originIataCode(),
                copy.destinationIataCode(),
                copy.aircraftRegistration(),
                compVersion
        );
        try {
            fleet.sendFlightToAircraft(canceledCopy);
        } catch (FleetUnavailableException | RestClientException e) {
            sagaCompensationPort.recordFailedCompensation(canceledCopy, "AIRCRAFT", e.getMessage());
        }
    }
}
