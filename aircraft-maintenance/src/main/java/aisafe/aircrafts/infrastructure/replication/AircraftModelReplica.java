package aisafe.aircrafts.infrastructure.replication;

import aisafe.aircrafts.domain.AircraftModel;
import aisafe.aircrafts.domain.AircraftModelImage;
import aisafe.aircrafts.domain.Manufacturer;

/**
 * An aircraft model as copied to the other replicas (catalog: every replica holds every model).
 */
public record AircraftModelReplica(
        String modelName,
        Manufacturer manufacturer,
        Double fuelCapacity,
        Double maxRange,
        Double cruisingSpeed,
        Integer maximumSeatingCapacity,
        byte[] imageBytes,
        String imageContentType
) {
    public static AircraftModelReplica from(AircraftModel model) {
        AircraftModelImage image = model.getImage();
        return new AircraftModelReplica(model.getModelName(), model.getManufacturer(), model.getFuelCapacity(),
                model.getMaxRange(), model.getCruisingSpeed(), model.getMaximumSeatingCapacity(),
                image == null ? null : image.getBytes(), image == null ? null : image.getContentType());
    }

    public AircraftModel toDomain() {
        AircraftModelImage image = imageBytes == null ? null : new AircraftModelImage(imageBytes, imageContentType);
        return new AircraftModel(modelName, manufacturer, fuelCapacity, maxRange, cruisingSpeed, image, maximumSeatingCapacity);
    }
}
