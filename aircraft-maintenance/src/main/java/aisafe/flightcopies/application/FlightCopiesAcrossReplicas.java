package aisafe.flightcopies.application;

import aisafe.flightcopies.domain.FlightCopy;

import java.util.List;

/**
 * Every flight copy, whichever replica holds it. A copy is kept by the replica that received it
 * from flight-ops (and the next one), so reports over flights read through this port.
 */
public interface FlightCopiesAcrossReplicas {

    /** Every flight copy once (its newest version). */
    List<FlightCopy> findAll();
}
