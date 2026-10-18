-- One row per origin and destination: replication identifies a route by its two airports.
-- Before this rule, a replica that received the same route twice at once (a copy from a peer and
-- the periodic sync) could store it twice. Keep the oldest row and drop the others with their
-- history, then add the rule.
DELETE FROM route_history WHERE route_id IN (
    SELECT r.id FROM route r WHERE EXISTS (
        SELECT 1 FROM route o
        WHERE o.origin_iata_code = r.origin_iata_code
          AND o.destination_iata_code = r.destination_iata_code
          AND o.id < r.id));

DELETE FROM route r WHERE EXISTS (
    SELECT 1 FROM route o
    WHERE o.origin_iata_code = r.origin_iata_code
      AND o.destination_iata_code = r.destination_iata_code
      AND o.id < r.id);

ALTER TABLE route ADD CONSTRAINT IF NOT EXISTS uk_route_origin_destination
    UNIQUE (origin_iata_code, destination_iata_code);
