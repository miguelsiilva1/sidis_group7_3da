package aisafe.maintenance.application;

import aisafe.aircrafts.application.dtos.AircraftForServicesResponse;
import aisafe.shared.application.PeerQueries;
import aisafe.shared.application.UseCase;
import aisafe.aircrafts.domain.*;
import aisafe.maintenance.application.dtos.CreateMaintenanceRecordRequest;
import aisafe.maintenance.application.dtos.MaintenanceRecordResponse;
import aisafe.maintenance.domain.*;
import aisafe.shared.domain.DuplicateResourceException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@UseCase
public class CreateMaintenanceRecordUseCase {
    private final MaintenanceRecordRepository recordRepository;
    private final MaintenancePartRepository partRepository;
    private final MaintenanceTemplateRepository templateRepository;
    private final AircraftRepository aircraftRepository;
    private final PeerQueries peerQueries;
    private final MaintenanceRecordsAcrossReplicas records;

    public CreateMaintenanceRecordUseCase(MaintenanceRecordRepository recordRepository, MaintenancePartRepository partRepository, MaintenanceTemplateRepository templateRepository, AircraftRepository aircraftRepository, PeerQueries peerQueries, MaintenanceRecordsAcrossReplicas records) {
        this.recordRepository = recordRepository;
        this.partRepository = partRepository;
        this.templateRepository = templateRepository;
        this.aircraftRepository = aircraftRepository;
        this.peerQueries = peerQueries;
        this.records = records;
    }

    public MaintenanceRecordResponse execute(CreateMaintenanceRecordRequest request) {
        List<MaintenancePart> parts = request.parts().stream()
                .map(pn -> partRepository.findByPartNumber(pn)
                        .orElseThrow(() -> new MaintenancePartNotFoundException("Part '" + pn + "' not found.")))
                .collect(Collectors.toList());
        MaintenanceTemplate template = templateRepository.findByName(request.template())
                .orElseThrow(() -> new MaintenanceTemplateNotFoundException("Template '" + request.template() + "' not found."));
        // The aircraft may be held by another replica (aircrafts are partitioned): ask the peers.
        RegistrationNumber aircraftRegistration = new RegistrationNumber(request.registrationNumber());
        boolean aircraftExists = aircraftRepository.existsByRegistrationNumber(aircraftRegistration)
                || peerQueries.fetchFirst("/internal/aircrafts/" + aircraftRegistration.getNumber(), AircraftForServicesResponse.class).isPresent();
        if (!aircraftExists) {
            throw new AircraftNotFoundException("Aircraft with registration number '" + request.registrationNumber() + "' not found.");
        }

        MaintenanceRecord record = new MaintenanceRecord(
                UUID.randomUUID(), request.description(), request.startDate(), request.expectedDuration(),
                parts, request.notes(), template, request.status(), request.components(),
                aircraftRegistration, request.cost(), null
        );

        // Records are partitioned: the same record may have been created on another replica (best effort)
        boolean duplicate = records.findAll().stream()
                .map(v -> v.value())
                .anyMatch(r -> r.getStartDate().equals(record.getStartDate())
                        && r.getTemplate().getName().equals(record.getTemplate().getName()));
        if (duplicate) {
            throw new DuplicateResourceException("Record with the same start date and template already exists.");
        }

        recordRepository.save(record);

        Long version = recordRepository.findVersionFor(record.getRecordId());

        return new MaintenanceRecordResponse(
                record.getRecordId(), record.getDescription(), record.getStartDate(),
                record.getExpectedDuration(), record.getNotes(),
                record.getParts().stream().map(MaintenancePart::getPartNumber).collect(Collectors.toList()),
                record.getTemplate().getName(),
                record.getStatus().name(), request.registrationNumber(),
                version,
                record.getComponents().stream().map(Enum::name).collect(Collectors.toSet()),
                record.getCost()
        );
    }
}
