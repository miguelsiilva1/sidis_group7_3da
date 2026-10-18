package aisafe.maintenance.domain;

import aisafe.shared.domain.NotFoundException;

/**
 * Thrown when a requested maintenance part cannot be found in the system.
 */
public class MaintenanceRecordNotFoundException extends NotFoundException {
    public MaintenanceRecordNotFoundException(String message) {
        super(message);
    }
}
