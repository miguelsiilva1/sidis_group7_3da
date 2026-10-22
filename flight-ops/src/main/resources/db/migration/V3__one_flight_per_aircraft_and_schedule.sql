-- One row per aircraft registration and departure date/time: replication identifies a flight
-- by its natural key. Before this rule, a replica that received the same flight twice at once
-- (a copy from a peer and periodic sync) could store it twice. Keep the oldest row and drop
-- the others, then add the unique constraint.
DELETE FROM scheduled_flight f WHERE EXISTS (
    SELECT 1 FROM scheduled_flight o
    WHERE o.aircraft_registration = f.aircraft_registration
      AND o.departure_date_time = f.departure_date_time
      AND o.id < f.id);

ALTER TABLE scheduled_flight ADD CONSTRAINT IF NOT EXISTS uk_flight_aircraft_departure
    UNIQUE (aircraft_registration, departure_date_time);
