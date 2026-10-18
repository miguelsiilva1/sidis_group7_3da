package aisafe.aircrafts.application;

import aisafe.aircrafts.domain.Aircraft;

import java.util.List;

/**
 * Every aircraft, whichever replica holds it. Aircraft are partitioned between the replicas, so
 * listings, reports and checks that must see the whole fleet read through this port instead of
 * the local repository.
 */
public interface AircraftAcrossReplicas {

    /** Every aircraft once (its newest version), ordered by registration. */
    List<Aircraft> findAll();
}
