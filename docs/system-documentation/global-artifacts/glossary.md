# Glossary of the distributed system

Terms of the distribution (SIDIS). The business terms (aircraft, route, maintenance record...) are in the
[monolith's glossary](../../../docs_monolith/system-documentation/global-artifacts/glossary.md).

## Services and machines

| Term            | Meaning                                                                                                                                                                                                                                                              |
| --------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Service**     | One of the independent applications the monolith was split into: aircraft-maintenance, airports-routes, flight-ops, plus the login. Each owns its data and talks to the others only over HTTP.                                                                       |
| **Replica**     | One running copy of a service, with its own database (`aircraft-maintenance-0`, `-1`, `-2`). Every service runs three.                                                                                                                                               |
| **Peer**        | Another replica of the same service.                                                                                                                                                                                                                                 |
| **Node**        | A machine of the k3s cluster (a VM on Proxmox in the reference cluster: `k3s1`, `k3s2`, `k3s3`). Each runs one replica of every service.                                                                                                                             |
| **k3s**         | A small Kubernetes. It keeps the replicas running, restarts them when they fail, and gives each a stable name (StatefulSet).                                                                                                                                         |
| **StatefulSet** | The kind of k3s workload used for the replicas: stable names (`-0`, `-1`, `-2`) and one disk per replica that follows it.                                                                                                                                            |
| **Virtual IP**  | An address of a service (e.g. `192.168.124.25` for aircraft-maintenance) that is not tied to one machine: MetalLB announces it from one node and moves it to another when that node is lost.                                                                         |
| **MetalLB**     | The component of the cluster that gives out and moves the virtual IPs (layer 2).                                                                                                                                                                                     |
| **Entry**       | Envoy, one per node, behind the virtual IPs. It ends HTTPS, sends each request to a ready replica and, when one fails, tries another. The services also call each other through the entry of their own node (`aircraft-maintenance-local`, `airports-routes-local`). |
| **Readiness**   | Whether a replica may receive requests. A replica reports ready only after it caught up with its peers; the entries check it every second.                                                                                                                           |
| **Drain**       | A replica told to stop first reports not ready, then keeps answering for a few seconds, so that no request in flight is lost.                                                                                                                                        |
| **Vigia**       | The watcher of the machines, inside the cluster: turns a stopped VM back on, and isolates a node that does not come back so that its replicas are reborn on the others.                                                                                              |
| **Isolation**   | Marking a lost node `out-of-service`, so that k3s recreates its replicas elsewhere.                                                                                                                                                                                  |
| **Node port**   | A port open on every node that leads straight to one replica (30810-30812, 30820-30822, 30830-30832), used by the tests to choose the replica.                                                                                                                       |

## Data

| Term                                    | Meaning                                                                                                                                                              |
| --------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Catalog**                             | Data that is small, rarely changed and needed by most writes (aircraft models, templates, parts, airports, certifications, accounts): every replica holds all of it. |
| **Record**                              | Data that is partitioned (aircraft, maintenance records, routes, flights, flight copies): each one lives on two replicas, its home and the next one in the ring.     |
| **Home**                                | The replica that created a record.                                                                                                                                   |
| **Ring**                                | The order of the replicas used to place records: `-0 → -1 → -2 → -0`. A record lives on its home and on the next replica.                                            |
| **Flight copy**                         | A copy of a flight that flight-ops sends to aircraft-maintenance (C4) and airports-routes (C5), so that they can answer about flights without asking flight-ops.     |
| **Flight id rule**                      | Each flight-ops replica gives the flight ids that end in its own digit (1, 11, 21... on `flight-ops-0`), so two replicas never give the same id.                     |
| **Domain identity / database identity** | The key the API shows (registration, IATA codes, flight id) is apart from the database's own row number, which is never shown (as in PSOFT).                         |

## Replication and consistency

| Term                       | Meaning                                                                                                                                                                                                       |
| -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Replication**            | Copying every change to the other replicas that must hold it.                                                                                                                                                 |
| **Outbox**                 | A table where a replica writes, in the same transaction as the change, one command per replica that must receive it; a dispatcher sends them right after the commit and repeats the ones that fail, in order. |
| **ReplicaSync**            | Every 30 s each replica asks its peers for what it should hold, and takes what it missed (anti-entropy). A replica that starts empty recovers this way before it reports ready.                               |
| **Stamp**                  | The time of a change plus the replica id (to break ties). It decides which of two changes is the latest.                                                                                                      |
| **Last writer wins**       | Of two changes to the same data, the one with the later stamp is kept.                                                                                                                                        |
| **Tombstone**              | What a deletion leaves behind, so that an older copy arriving later cannot bring the data back.                                                                                                               |
| **Eventual consistency**   | A write is answered by one replica and reaches the others shortly after; for a moment two replicas can answer differently.                                                                                    |
| **CAP, AP**                | Of consistency, availability and partition tolerance, the system keeps availability and partition tolerance (AP): it keeps answering during a failure, with eventual consistency.                             |
| **Optimistic concurrency** | Inside one replica, a change carries the version it read (`If-Match`, `ETag`); a stale version is refused (412).                                                                                              |

## Requests between replicas

| Term                        | Meaning                                                                                                                                                                                               |
| --------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Forwarding**              | A replica that does not hold the requested record asks its peers for it (peer to peer) and answers with the first holder's response.                                                                  |
| **Gathering (aggregation)** | For a listing or a report, the replica collects the records of every replica (`.../AcrossReplicas`, `GET /internal/replicas/{type}`), keeps the latest change of each, and computes over all of them. |
| **Partial result**          | An answer computed without some records, because both replicas that hold them could not be asked: marked with `X-Partial-Result: true`.                                                               |
| **`X-Node-Id`**             | Response header: the replica that answered.                                                                                                                                                           |
| **`X-Data-Node`**           | Response header: the replica that held the data, when it was forwarded.                                                                                                                               |
| **`X-Peer-Forwarded`**      | Request header: the request was already forwarded by a peer; it is never forwarded again (no loops).                                                                                                  |
| **`X-Unreachable-Peers`**   | Response header: the peers that could not be asked.                                                                                                                                                   |
| **`X-Correlation-Id`**      | One id per client request, given by the entry and passed to every replica and service it reaches; it is in every log line.                                                                            |
| **Timeout**                 | How long a replica waits for a peer: 0.5 s to connect, 2 s to answer.                                                                                                                                 |
| **Retry with backoff**      | One more attempt after a short wait when a peer fails.                                                                                                                                                |
| **Circuit breaker**         | After 3 failures in a row a peer is skipped for 10 s, so that requests do not keep waiting for it.                                                                                                    |
| **503 with `Retry-After`**  | The answer when data may exist on a replica that could not be asked: "try again", never a 404 for data that may exist.                                                                                |

## Between services

| Term                | Meaning                                                                                                                                                                                                           |
| ------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Contract**        | One of the five HTTP calls between services (C1-C5), with its request, answers and failures.                                                                                                                      |
| **Command / query** | A call that asks for something to be done (`PUT` a copy) or for data (`GET`), and waits for the answer. Assignment 1 uses only these: no events or message queues.                                                |
| **Saga**            | Scheduling a flight is a sequence of local steps in three services (check the route, check the aircraft, store the flight, copy it to aircraft-maintenance and airports-routes), not one distributed transaction. |
| **Compensation**    | The step that undoes an earlier step of a Saga when a later one fails (e.g. cancel the copy already sent). One that cannot be delivered is stored and retried every 5 s.                                          |
| **Idempotent**      | Sending the same request again changes nothing (the flight copies).                                                                                                                                               |

## Security

| Term                        | Meaning                                                                                                                                                     |
| --------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **JWT**                     | The token the login issues; every service checks it by itself.                                                                                              |
| **RBAC**                    | Role-based access control: each route of each service allows some roles (ADMIN, BACKOFFICE_OPERATOR, ATCC, MAINTENANCE_TECHNICIAN, MAINTENANCE_SUPERVISOR). |
| **Service token**           | A token with the role `SERVICE`, made by a replica, for replication between replicas and for calls outside a user's request.                                |
| **HTTPS / TLS**             | Encryption from the client to the entry; the virtual IPs answer only on port 443.                                                                           |
| **Self-signed certificate** | The entries' certificate, made by `deploy/setup.sh` for the four virtual IPs; clients trust it through `deploy/entry.crt`.                                  |
| **Audit trail**             | One JSON line per request to the API (who, what, when, where, result), in a file of its own on each replica's disk.                                         |
