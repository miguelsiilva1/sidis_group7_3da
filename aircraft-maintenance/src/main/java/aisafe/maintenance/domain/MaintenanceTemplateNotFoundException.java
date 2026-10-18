package aisafe.maintenance.domain;

import aisafe.shared.domain.NotFoundException;

/**
 * Thrown when a requested maintenance template cannot be found in the system
 */
public class MaintenanceTemplateNotFoundException extends NotFoundException {
    public MaintenanceTemplateNotFoundException(String message) {
        super(message);
    }
}
