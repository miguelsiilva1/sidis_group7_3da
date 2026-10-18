package aisafe.airports.domain;

import java.util.Objects;

/**
 * Name of an aircraft model, as used by the certifications of an airport. Aircraft models belong
 * to aircraft-maintenance; this service keeps its own copy of the type (D1).
 */
public final class ModelName {
    private final String name;

    public ModelName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new InvalidAircraftCertificationException("Aircraft model name cannot be blank.");
        }
        this.name = name.trim();
    }

    public String getName() { return name; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModelName that = (ModelName) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
