# US228 - Export route network data

| Service         | Work package (monolith) | Monolith documentation                                                                     |
| --------------- | ----------------------- | ------------------------------------------------------------------------------------------ |
| airports-routes | BONUS                   | [US228](../../../../../docs_monolith/system-documentation/use-cases/BONUS/US228/README.md) |

## User Story

> As a Backoffice Operator, I want to export route network data in standard aviation formats (GeoJSON, KML) so that I can visualize the network in external GIS tools.

## Endpoints

| Method | Route                               | Roles | Use case            |
| ------ | ----------------------------------- | ----- | ------------------- |
| GET    | `/api/routes/export?format={geojson | kml}` | Backoffice Operator | `ExportRouteNetworkUseCase` |

Through the virtual IP of the service, HTTPS only, with a JWT issued by the login.

## Acceptance Criteria

- Support for GeoJSON export.
- Support for KML export.
- Coordinates must be fetched from the Airport context/aggregate.
- Integration test verifies the download endpoint.

In the distributed system:

- The answer covers the records of every replica, not only those of the replica that answers.
- Every request and its answer are in the audit trail of the replica that answered.

## Pre-conditions

- The actor is authenticated through the login (JWT) with a role allowed for the route (see Endpoints).
- At least one replica of the service is ready.

## Post-conditions

- Nothing is changed (a read).

## Main Success Scenario

1. The actor sends `GET /api/routes/export?format={geojson|kml}` with the JWT to the virtual IP of airports-routes (HTTPS); the entry (Envoy) of the machine that holds that IP passes it to a ready replica of airports-routes, and tries another replica if that one fails.
2. `ExportRouteNetworkUseCase` gathers the routes of every replica: its own, plus those of each peer (`GET /internal/replicas/{type}`, all peers in parallel), keeping the latest change of each record and leaving out deleted ones. It reads the airports from its own copy of the catalog.
3. It filters, computes and sorts over the whole set, then answers 200.

## Alternative / Exception Flows

| Condition                                              | System Response                                                                           |
| ------------------------------------------------------ | ----------------------------------------------------------------------------------------- |
| No valid token                                         | 401                                                                                       |
| The actor's role is not Backoffice Operator            | 403                                                                                       |
| Both replicas that hold some record could not be asked | 200 with `X-Partial-Result: true`                                                         |
| The replica that got the request stops answering       | the entry repeats a read on another replica; a write only if it never reached the replica |

## Distribution

The answer covers **every replica**: the records are partitioned (each on two replicas), so the replica that answers gathers them from all of them (`RoutesAcrossReplicas`).

## Tests

Not in the distributed Postman collection; covered by the unit and integration tests of airports-routes (`./mvnw test`), and served by the same forwarding, gathering and replication as the routes the collection does test.

## Sequence Diagrams

### System Sequence Diagram (SSD)

![SSD US228](svg/ssd_us228.svg)

### Sequence Diagram (SD)

![SD US228](svg/sd_us228.svg)

Sources: [`puml/ssd_us228.puml`](puml/ssd_us228.puml), [`puml/sd_us228.puml`](puml/sd_us228.puml).
