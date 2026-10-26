-- Each replica numbered its flights from 1 with its own database, so the same number named
-- different flights on different replicas, and replication, the gathered lists and the copies sent
-- to the other services (C4, C5) mixed them up. The flight's id (the one in the API) is now
-- flight_id, given by the replica that creates the flight from numbers no other replica gives;
-- id stays as this database's own row number, never seen outside it.
-- Flights from before keep their number. On a cluster that already scheduled flights on more than
-- one replica, those numbers may repeat across replicas: clear the flights (and their copies).
ALTER TABLE scheduled_flight ADD COLUMN flight_id bigint;
UPDATE scheduled_flight SET flight_id = id;
ALTER TABLE scheduled_flight ALTER COLUMN flight_id SET NOT NULL;
ALTER TABLE scheduled_flight ADD CONSTRAINT uk_flight_id UNIQUE (flight_id);
