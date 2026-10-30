# FURPS+ of the distributed system

> Sources: the SIDIS Assignment 1 statement and lab guides, and the
> [monolith's FURPS+](../../../docs_monolith/system-documentation/global-artifacts/furps.md), whose
> business requirements still hold. Each requirement says where the system meets it.

## Functional

- Every user story of the monolith is still served, by the service that owns its data:
  [use cases](../use-cases/README.md). Two routes moved to the service that owns the data they need
  (US203 compatible routes and US227 fuel efficiency, now under `/api/routes/...` in airports-routes).
- Each service owns its data and logic; services talk only through five HTTP contracts
  ([C1-C5](../contracts/README.md)), commands or queries only (no events or message queues in
  Assignment 1).
- A request reaching any replica gets the same answer: a replica that does not hold the data asks its
  peers (forwarding), and listings and reports cover every replica (gathering).
- Scheduling a flight is a Saga across three services, with compensations ([US212](../use-cases/flight-ops/US212/README.md)).

## Usability

- REST over HTTPS, with the same routes, status codes and HATEOAS links as the monolith.
- Each replica serves its own OpenAPI documentation and Swagger UI (`/swagger-ui.html`), through the
  virtual IP of its service.
- Every response says which replica answered (`X-Node-Id`) and, when forwarded, which one held the data
  (`X-Data-Node`); an incomplete answer says so (`X-Partial-Result`, `X-Unreachable-Peers`).
- A demo dashboard shows the requests travelling through the cluster
  ([deploy/README.md](../../../deploy/README.md#watching-it-live-demo-dashboard)).

## Reliability

- **Availability with one failure at a time** (a replica, a login, a machine powered off or frozen, a
  redeploy): no request fails or gets a wrong answer. Three replicas per service, one per machine,
  behind virtual IPs that move to another machine (MetalLB) and entries that retry on another replica
  (Envoy). Measured with `deploy/manifests/failure-test.sh` on 2026-10-17: 0 failed or wrong requests
  out of 9606, in the five scenarios.
- **No data lost when one replica or machine is lost**: every record is on two replicas, every catalog
  on all three, each replica on its own disk.
- **Self-healing**: k3s restarts failed replicas; the vigia turns stopped machines back on and isolates
  those that do not come back, so their replicas are reborn on the others and recover their data from
  their peers before reporting ready.
- **Consistency model**: AP with eventual consistency, last writer wins with stamps and tombstones
  ([system documentation, section 8](../../README.md#8-consistency-model)).
- **Never a wrong "not found"**: when a replica that may hold the data could not be asked, the answer
  is 503 with `Retry-After`, never 404.

## Performance

- A replica waits at most 0.5 s to connect to a peer and 2 s for its answer, with one retry, and skips a
  peer that keeps failing (circuit breaker, 3 failures → 10 s).
- Peers are asked in parallel (forwarded reads and gathering).
- Writes are answered as soon as they are stored on one replica; the copies are sent right after the
  commit (outbox).
- The entry gives each read attempt 5 s before trying another replica, 60 s in all.

## Supportability

- One command builds or updates the whole environment, on any Proxmox or on existing machines:
  `deploy/setup.sh` (configuration in `deploy/config.sh`), and it can be run again safely.
- Container images built by GitHub Actions for every commit and published on the GitHub registry; any
  commit can be deployed (`IMAGE_TAG`).
- Database structure managed by Flyway (numbered migrations per service).
- Tests: unit and integration tests per module (`./mvnw test`, run by CI), the distributed Postman
  collection, and the failure test.
- Every log line carries the replica and the correlation id, so one request can be followed through every
  replica and service.

## Security

- Users authenticate on the login (JWT); every service checks the token by itself, without calling the
  login.
- Role-based access control on every route of every service (the monolith's rules); 401 without a valid
  token, 403 with the wrong role.
- Replication between replicas needs a service token (role `SERVICE`); calls between services carry the
  user's token, or the replica's service token outside a user's request.
- Encryption in transit from the client to the cluster: HTTPS only on the virtual IPs (TLS 1.2 or later).
- Audit trail: every request to the API, also the refused ones, in a file of its own on each replica's
  disk.
- Limits: inside the cluster requests go in plain HTTP, and the tokens use one shared secret
  ([known limitations](../../README.md#13-known-limitations)).

## + (Design, implementation, interface and physical constraints)

- **Design**: three services by business capability plus a login; dependencies one way only
  (flight-ops → airports-routes → aircraft-maintenance); shared distribution code in the `common`
  library, no shared domain classes.
- **Implementation**: Java 25, Spring Boot 4, Maven multi-module; H2 file database per replica.
- **Interface**: REST + JSON (HAL); contracts C1-C5 under `/internal/**`; JWT bearer tokens.
- **Physical**: three VMs on Proxmox (4 GB each) running k3s, MetalLB and Envoy; a machine of the same
  network to reach the virtual IPs.
