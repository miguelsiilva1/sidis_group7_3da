package aisafe.flights.application;

import aisafe.flights.domain.ScheduledFlight;
import aisafe.shared.application.Versioned;

import java.util.List;

/**
 * Every scheduled flight, whichever replica holds it. Scheduled flights are partitioned
 * between the replicas, so listings, reports and checks over flights read through this port
 * instead of the local repository.
 */
public interface FlightsAcrossReplicas {

    /** Every flight once (its newest version), with that version. */
    List<Versioned<ScheduledFlight>> findAll();
}
