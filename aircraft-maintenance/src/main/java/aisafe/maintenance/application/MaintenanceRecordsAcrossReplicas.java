package aisafe.maintenance.application;

import aisafe.maintenance.domain.MaintenanceRecord;
import aisafe.shared.application.Versioned;

import java.util.List;

/**
 * Every maintenance record, whichever replica holds it. Records are partitioned between the
 * replicas, so listings, reports and checks over records read through this port instead of the
 * local repository.
 */
public interface MaintenanceRecordsAcrossReplicas {

    /** Every record once (its newest version), with that version. */
    List<Versioned<MaintenanceRecord>> findAll();
}
