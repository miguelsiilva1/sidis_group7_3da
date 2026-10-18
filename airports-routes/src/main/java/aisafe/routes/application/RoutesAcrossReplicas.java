package aisafe.routes.application;

import aisafe.routes.domain.Route;
import aisafe.shared.application.Versioned;

import java.util.List;

/**
 * Every route, whichever replica holds it. Routes are partitioned between the replicas, so
 * listings, reports and checks over routes read through this port instead of the local repository.
 */
public interface RoutesAcrossReplicas {

    /** Every route once (its newest version), with that version, ordered by origin and destination. */
    List<Versioned<Route>> findAll();
}
