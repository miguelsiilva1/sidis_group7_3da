package aisafe.airports.infrastructure.replication;

import aisafe.airports.domain.Airport;
import aisafe.airports.domain.AirportPhoto;
import aisafe.airports.domain.AirportStatus;
import aisafe.airports.domain.Contact;
import aisafe.airports.domain.ContactType;
import aisafe.airports.domain.Gate;
import aisafe.airports.domain.Runway;
import aisafe.airports.domain.Service;
import aisafe.airports.domain.Terminal;

import java.util.List;

/**
 * An airport as copied to the other replicas (catalog: every replica holds every airport, since
 * routes and certifications on any replica refer to them).
 */
public record AirportReplica(
        String iataCode,
        String name,
        String city,
        String country,
        String region,
        String timezone,
        Double latitude,
        Double longitude,
        List<RunwayData> runways,
        AirportStatus status,
        List<PhotoData> photos,
        String operationalHours,
        List<ContactData> contacts,
        List<String> services,
        List<String> terminals,
        List<String> gates
) {
    public static AirportReplica from(Airport airport) {
        return new AirportReplica(airport.getIataCode().getCode(), airport.getName(), airport.getCity(),
                airport.getCountry(), airport.getRegion(), airport.getTimezone(),
                airport.getCoordinates().getLatitude(), airport.getCoordinates().getLongitude(),
                airport.getRunways().stream().map(r -> new RunwayData(r.getName(), r.getLength(), r.getOrientation())).toList(),
                airport.getStatus(),
                airport.getPhotos().stream().map(p -> new PhotoData(p.getBytes(), p.getContentType())).toList(),
                airport.getOperationalHours(),
                airport.getContacts().stream().map(c -> new ContactData(c.getType(), c.getValue(), c.getDescription())).toList(),
                airport.getServices().stream().map(Service::getDescription).toList(),
                airport.getTerminals().stream().map(Terminal::getName).toList(),
                airport.getGates().stream().map(Gate::getIdentifier).toList());
    }

    public Airport toDomain() {
        Airport airport = new Airport(iataCode, name, city, country, region, timezone, latitude, longitude,
                runways.stream().map(r -> new Runway(r.name(), r.length(), r.orientation())).toList());
        airport.updateDetails(operationalHours,
                contacts.stream().map(c -> new Contact(c.type(), c.value(), c.description())).toList(),
                photos.stream().map(p -> new AirportPhoto(p.bytes(), p.contentType())).toList(),
                services.stream().map(Service::new).toList(),
                terminals.stream().map(Terminal::new).toList(),
                gates.stream().map(Gate::new).toList());
        airport.changeStatus(status);
        return airport;
    }

    public record RunwayData(String name, Integer length, String orientation) {
    }

    public record PhotoData(byte[] bytes, String contentType) {
    }

    public record ContactData(ContactType type, String value, String description) {
    }
}
