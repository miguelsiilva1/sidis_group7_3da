package aisafe.aircrafts.application;

import aisafe.flightcopies.domain.FlightCopyStatus;
import aisafe.flightcopies.application.FlightCopiesAcrossReplicas;
import aisafe.aircrafts.application.dtos.GetAircraftUtilizationRequest;
import aisafe.aircrafts.application.dtos.UtilizationDataPointResponse;
import aisafe.aircrafts.domain.AircraftNotFoundException;
import aisafe.aircrafts.domain.RegistrationNumber;
import aisafe.flightcopies.domain.FlightCopy;
import aisafe.shared.application.UseCase;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@UseCase(readOnly = true)
public class GetAircraftUtilizationUseCase {

    private final AircraftAcrossReplicas aircraft;
    private final FlightCopiesAcrossReplicas flightCopies;

    public GetAircraftUtilizationUseCase(AircraftAcrossReplicas aircraft, FlightCopiesAcrossReplicas flightCopies) {
        this.aircraft = aircraft;
        this.flightCopies = flightCopies;
    }

    public List<UtilizationDataPointResponse> execute(GetAircraftUtilizationRequest request) {
        String registration = request.registration();
        LocalDate startDate = request.startDate();
        LocalDate endDate = request.endDate();
        RegistrationNumber reg = new RegistrationNumber(registration);
        if (aircraft.findAll().stream().noneMatch(a -> a.getRegistrationNumber().equals(reg))) {
            throw new AircraftNotFoundException("Aircraft not found.");
        }

        OffsetDateTime start = startDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = endDate.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC).minusNanos(1);

        // Completed flights of this aircraft that departed in the period, from every replica
        List<FlightCopy> flights = flightCopies.findAll().stream()
                .filter(f -> f.getAircraftRegistrationNumber().equals(reg))
                .filter(f -> f.getStatus() == FlightCopyStatus.COMPLETED)
                .filter(f -> !f.getDepartureDateTime().isBefore(start) && !f.getDepartureDateTime().isAfter(end))
                .toList();

        Map<LocalDate, Double> dailyHours = new TreeMap<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            dailyHours.put(date, 0.0);
        }

        for (FlightCopy flight : flights) {
            LocalDate flightDate = flight.getDepartureDateTime().toLocalDate();
            if (dailyHours.containsKey(flightDate)) {
                double hours = Duration.between(flight.getDepartureDateTime(), flight.getArrivalDateTime()).toMinutes() / 60.0;
                dailyHours.put(flightDate, dailyHours.get(flightDate) + hours);
            }
        }

        return dailyHours.entrySet().stream()
                .map(entry -> new UtilizationDataPointResponse(
                        entry.getKey(),
                        entry.getValue(),
                        (entry.getValue() / 24.0) * 100.0
                ))
                .collect(Collectors.toList());
    }
}
