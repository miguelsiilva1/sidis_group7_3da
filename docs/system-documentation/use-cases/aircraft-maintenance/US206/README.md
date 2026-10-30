# US206 - Calculate Total Operational Hours

| Service              | Work package (monolith) | Monolith documentation                                                                    |
| -------------------- | ----------------------- | ----------------------------------------------------------------------------------------- |
| aircraft-maintenance | WP1B                    | [US206](../../../../../docs_monolith/system-documentation/use-cases/WP1B/US206/README.md) |

## User Story

> As an ATCC, I want to calculate the total operational hours for an aircraft based on its flight history to schedule maintenance appropriately.

## Endpoints

| Method | Route                                             | Roles | Use case                                   |
| ------ | ------------------------------------------------- | ----- | ------------------------------------------ |
| GET    | `/api/aircrafts/{registration}/operational-hours` | ATCC  | `CalculateAircraftOperationalHoursUseCase` |

Through the virtual IP of the service, HTTPS only, with a JWT issued by the login.

## Acceptance Criteria

- The request must specify the aircraft by its `registrationNumber` in the URL.
- The operational hours are calculated as the sum of all flight durations for that aircraft.
- The hours are summed over the flight copies gathered from every replica (in the monolith, by a native SQL query).
- The result is returned as a number (representing hours).
- If the aircraft does not exist, returns HTTP 404.
- If the aircraft exists but has no flights, returns 0.

In the distributed system:

- The answer covers the records of every replica, not only those of the replica that answers.
- Every request and its answer are in the audit trail of the replica that answered.

## Pre-conditions

- The actor is authenticated through the login (JWT) with a role allowed for the route (see Endpoints).
- At least one replica of the service is ready.

## Post-conditions

- No state change occurs; the use case is read-only.

## Main Success Scenario

1. The actor sends `GET /api/aircrafts/{registration}/operational-hours` with the JWT to the virtual IP of aircraft-maintenance (HTTPS); the entry (Envoy) of the machine that holds that IP passes it to a ready replica of aircraft-maintenance, and tries another replica if that one fails.
2. `CalculateAircraftOperationalHoursUseCase` gathers the aircraft, the copies of the flights (sent by flight-ops) of every replica: its own, plus those of each peer (`GET /internal/replicas/{type}`, all peers in parallel), keeping the latest change of each record and leaving out deleted ones.
3. It filters, computes and sorts over the whole set, then answers 200.

## Alternative / Exception Flows

| Condition                                              | System Response                                                                           |
| ------------------------------------------------------ | ----------------------------------------------------------------------------------------- |
| No valid token                                         | 401                                                                                       |
| The actor's role is not ATCC                           | 403                                                                                       |
| Both replicas that hold some record could not be asked | 200 with `X-Partial-Result: true`                                                         |
| The replica that got the request stops answering       | the entry repeats a read on another replica; a write only if it never reached the replica |

## Distribution

The answer covers **every replica**: the records are partitioned (each on two replicas), so the replica that answers gathers them from all of them (`AircraftAcrossReplicas`, `FlightCopiesAcrossReplicas`).

## Tests

Not in the distributed Postman collection; covered by the unit and integration tests of aircraft-maintenance (`./mvnw test`), and served by the same forwarding, gathering and replication as the routes the collection does test.

## Sequence Diagrams

### System Sequence Diagram (SSD)

![SSD US206](svg/ssd_us206.svg)

### Sequence Diagram (SD)

![SD US206](svg/sd_us206.svg)

Sources: [`puml/ssd_us206.puml`](puml/ssd_us206.puml), [`puml/sd_us206.puml`](puml/sd_us206.puml).
