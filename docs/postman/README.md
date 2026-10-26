# Postman: distributed tests

`aisafe-distributed.postman_collection.json` tests the distributed AISafe end to end, against the
cluster built by `deploy/setup.sh`: 84 requests, 197 checks. The collection of the monolith is in
[`docs_monolith/postman/`](../../docs_monolith/postman/psoft.postman_collection.json), kept for reference.

| Folder | What it shows |
|---|---|
| 0. Setup | One login per role (through the login's virtual IP), and the 3 replicas of each service ready |
| 1. Local access | Data held by the replica asked is answered by it, without asking its peers |
| 2. Forwarding between replicas | An aircraft and a route created on replica 0, read on replica 1 (holds the copy) and on replica 2 (forwards: `X-Data-Node` names the replica that held it); an unknown aircraft or route gives 404 after asking every peer |
| 3. Aggregation | The list holds the records of every replica; lists and reports (including route popularity, US214, which counts the flight copies of every replica) give the same data on the 3 replicas |
| 4. Catalogs | An aircraft model created on replica 2, and an airport created on replica 1, are held by the other replicas too |
| 5. Between services | airports-routes asks aircraft-maintenance whether a model (C3) or an aircraft (C1) exists, and for the fuel consumption of an aircraft (US227, C1) |
| 6. Flights: the scheduling Saga | One flight scheduled on each flight-ops replica (the Saga: route C2, aircraft C1, overlaps on every replica, copies C4 and C5); every replica lists all three; an overlapping flight held by another replica gives 409, an unknown aircraft or route 404; aircraft-maintenance (the aircraft cannot be deleted) and airports-routes (the route's flight count) hold the copies |
| 7. Access control | A role that may not do something gets 403; so does a request without a token |
| 8. Failures | With a replica of each service down, the same data is still answered by the others |
| 9. Cleanup | Deletes what the run created (through replicas that forward the deletion), so it can run again. The aircraft of folder 6 stays: flights cannot be canceled through the API, and an aircraft with flights cannot be deleted |

Every response says which replica answered (`X-Node-Id`), and, when forwarded, which replica held
the data (`X-Data-Node`). Peers that did not answer are listed in `X-Unreachable-Peers`. The run
prints these headers in the console.

## Running it

The virtual IPs answer only with HTTPS, with a self-signed certificate (`deploy/entry.crt`, saved by
`deploy/setup.sh`); the node ports of single replicas are plain HTTP.

**Postman:** import both files, select the environment "AISafe k3s", and run the collection (or
the folders in order). With a cluster other than the reference one, set `node` (any node's IP) and
the virtual IPs in the environment to the values of `deploy/config.sh`. To trust the certificate:
Settings → Certificates → CA certificates, select `deploy/entry.crt`.

**Command line** ([newman](https://www.npmjs.com/package/newman)):

```sh
newman run docs/postman/aisafe-distributed.postman_collection.json \
  -e docs/postman/aisafe-k3s.postman_environment.json --ssl-extra-ca-certs deploy/entry.crt
```

**Folder 8 with a replica really down:** run folders 0, 2 and 6, stop a replica, then run folder 8
(and folder 9 once it is back). In Postman, the environment keeps the values between runs; with
newman, pass them on with `--export-environment`:

```sh
newman run docs/postman/aisafe-distributed.postman_collection.json -e docs/postman/aisafe-k3s.postman_environment.json \
  --ssl-extra-ca-certs deploy/entry.crt --folder "0. Setup: logins and every replica ready" --folder "2. Forwarding between replicas (peer-to-peer)" \
  --folder "6. Flights: the scheduling Saga (flight-ops)" --export-environment /tmp/aisafe-env.json
kubectl -n aisafe delete pod aircraft-maintenance-1 airports-routes-1 flight-ops-1 --grace-period=0 --force
newman run docs/postman/aisafe-distributed.postman_collection.json -e /tmp/aisafe-env.json \
  --ssl-extra-ca-certs deploy/entry.crt --folder "8. Failures: with a replica down"
```
