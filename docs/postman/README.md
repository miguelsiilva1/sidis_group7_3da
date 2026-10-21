# Postman: distributed tests

`aisafe-distributed.postman_collection.json` tests the distributed AISafe end to end, against the
cluster built by `deploy/setup.sh`: 50 requests, 108 checks. `psoft.postman_collection.json` is the
collection of the monolith, kept for reference.

| Folder | What it shows |
|---|---|
| 0. Setup | One login per role (through the login's virtual IP), and the 3 replicas of each service ready |
| 1. Local access | Data held by the replica asked is answered by it, without asking its peers |
| 2. Forwarding between replicas | An aircraft and a route created on replica 0, read on replica 1 (holds the copy) and on replica 2 (forwards: `X-Data-Node` names the replica that held it); an unknown aircraft gives 404 after asking every peer |
| 3. Aggregation | The list holds the records of every replica; lists and reports give the same data on the 3 replicas |
| 4. Catalogs | An aircraft model created on replica 2 is held by replicas 0 and 1 too |
| 5. Between services | airports-routes asks aircraft-maintenance whether a model (C3) or an aircraft (C1) exists |
| 6. Access control | A role that may not do something gets 403; so does a request without a token |
| 7. Failures | With a replica down, the same data is still answered by the others |
| 8. Cleanup | Deletes what the run created (through replicas that forward the deletion), so it can run again |

Every response says which replica answered (`X-Node-Id`), and, when forwarded, which replica held
the data (`X-Data-Node`). Peers that did not answer are listed in `X-Unreachable-Peers`. The run
prints these headers in the console.

## Running it

**Postman:** import both files, select the environment "AISafe k3s", and run the collection (or
the folders in order). With a cluster other than the reference one, set `node` (any node's IP) and
the virtual IPs in the environment to the values of `deploy/config.sh`.

**Command line** ([newman](https://www.npmjs.com/package/newman)):

```sh
newman run docs/postman/aisafe-distributed.postman_collection.json \
  -e docs/postman/aisafe-k3s.postman_environment.json
```

**Folder 7 with a replica really down:** run folders 0 and 2, stop a replica, then run folder 7
(and folder 8 once it is back). In Postman, the environment keeps the values between runs; with
newman, pass them on with `--export-environment`:

```sh
newman run docs/postman/aisafe-distributed.postman_collection.json -e docs/postman/aisafe-k3s.postman_environment.json \
  --folder "0. Setup: logins and every replica ready" --folder "2. Forwarding between replicas (peer-to-peer)" \
  --export-environment /tmp/aisafe-env.json
kubectl -n aisafe delete pod aircraft-maintenance-1 --grace-period=0 --force
newman run docs/postman/aisafe-distributed.postman_collection.json -e /tmp/aisafe-env.json \
  --folder "7. Failures: with a replica down"
```
