package aisafe.flights.application;

import aisafe.fleet.application.Fleet;
import aisafe.fleet.domain.AircraftNotFoundException;
import aisafe.flights.application.dtos.FlightResponse;
import aisafe.flights.domain.RegistrationNumber;
import aisafe.flights.domain.ScheduledFlight;
import aisafe.shared.application.UseCase;
import aisafe.shared.application.Versioned;
import lombok.RequiredArgsConstructor;

import java.util.Comparator;
import java.util.List;

@UseCase(readOnly = true)
@RequiredArgsConstructor
public class ViewScheduledFlightsByAircraftUseCase {

    private final FlightsAcrossReplicas flightsAcrossReplicas;
    private final Fleet fleet;

    public List<FlightResponse> execute(String aircraftId) {
        String normalizedAircraftId = aircraftId.trim().toUpperCase();
        if (fleet.findAircraft(normalizedAircraftId).isEmpty()) {
            throw new AircraftNotFoundException(normalizedAircraftId);
        }
        return flightsAcrossReplicas.findAll().stream()
                .map(Versioned::value)
                .filter(flight -> flight.getAircraftRegistrationNumber().getNumber().equalsIgnoreCase(normalizedAircraftId))
                .sorted(Comparator.comparing(ScheduledFlight::getDepartureDateTime))
                .map(FlightResponse::from)
                .toList();
    }
}
