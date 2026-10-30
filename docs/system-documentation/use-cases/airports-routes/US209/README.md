# US209 - View Routes to/from an Airport

| Service         | Work package (monolith) | Monolith documentation                                                                    |
| --------------- | ----------------------- | ----------------------------------------------------------------------------------------- |
| airports-routes | WP2B                    | [US209](../../../../../docs_monolith/system-documentation/use-cases/WP2B/US209/README.md) |

## User Story

> As an **ATCC**, I want to view all routes that depart from or arrive at a specific airport.

## Endpoints

| Method | Route                             | Roles | Use case                   |
| ------ | --------------------------------- | ----- | -------------------------- |
| GET    | `/api/airports/{iataCode}/routes` | ATCC  | `ViewAirportRoutesUseCase` |

Through the virtual IP of the service, HTTPS only, with a JWT issued by the login.

## Acceptance Criteria

- The system returns all routes where the airport is either the origin or the destination.
- The airport must exist; otherwise HTTP 404 is returned.
- An empty list is returned if the airport has no associated routes.
- The system returns HTTP 200.

In the distributed system:

- The answer covers the records of every replica, not only those of the replica that answers.
- Every request and its answer are in the audit trail of the replica that answered.

## Pre-conditions

- The actor is authenticated through the login (JWT) with a role allowed for the route (see Endpoints).
- The target airport exists.
- At least one replica of the service is ready.

## Post-conditions

- No state change. Read-only operation.

## Main Success Scenario

1. The actor sends `GET /api/airports/{iataCode}/routes` with the JWT to the virtual IP of airports-routes (HTTPS); the entry (Envoy) of the machine that holds that IP passes it to a ready replica of airports-routes, and tries another replica if that one fails.
2. `ViewAirportRoutesUseCase` gathers the routes of every replica: its own, plus those of each peer (`GET /internal/replicas/{type}`, all peers in parallel), keeping the latest change of each record and leaving out deleted ones. It reads the airports from its own copy of the catalog.
3. It filters, computes and sorts over the whole set, then answers 200.

## Alternative / Exception Flows

| Condition                                              | System Response                                                                           |
| ------------------------------------------------------ | ----------------------------------------------------------------------------------------- |
| Airport not found                                      | HTTP 404                                                                                  |
| Airport exists but has no routes                       | HTTP 200 with empty list                                                                  |
| No valid token                                         | 401                                                                                       |
| The actor's role is not ATCC                           | 403                                                                                       |
| Both replicas that hold some record could not be asked | 200 with `X-Partial-Result: true`                                                         |
| The replica that got the request stops answering       | the entry repeats a read on another replica; a write only if it never reached the replica |

## Distribution

The answer covers **every replica**: the records are partitioned (each on two replicas), so the replica that answers gathers them from all of them (`RoutesAcrossReplicas`).

## Tests

Not in the distributed Postman collection; covered by the unit and integration tests of airports-routes (`./mvnw test`), and served by the same forwarding, gathering and replication as the routes the collection does test.

## Sequence Diagrams

### System Sequence Diagram (SSD)

![SSD US209](svg/ssd_us209.svg)

### Sequence Diagram (SD)

![SD US209](svg/sd_us209.svg)

Sources: [`puml/ssd_us209.puml`](puml/ssd_us209.puml), [`puml/sd_us209.puml`](puml/sd_us209.puml).
