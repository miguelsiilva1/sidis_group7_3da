package aisafe.maintenance.domain;

import aisafe.shared.domain.NotFoundException;

/**
 * Thrown when a requested maintenance part cannot be found in the system.
 */
public class MaintenancePartNotFoundException extends NotFoundException {
    public MaintenancePartNotFoundException(String message) {
        super(message);
    }
}
