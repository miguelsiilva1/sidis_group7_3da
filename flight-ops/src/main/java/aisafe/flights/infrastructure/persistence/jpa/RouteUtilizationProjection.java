package aisafe.flights.infrastructure.persistence.jpa;

public interface RouteUtilizationProjection {
    default Long getRouteId() {
        return null;
    }
    String getOriginCode();
    String getDestinationCode();
    Long getFlightCount();
}
