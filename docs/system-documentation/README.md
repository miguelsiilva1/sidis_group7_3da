# System documentation

Artifacts of the distributed system, organised as in the [monolith's documentation](../../docs_monolith/README.md):

| Folder                                         | Content                                                                                                      |
| ---------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| [global-artifacts](global-artifacts/README.md) | C4 context and containers, logical and physical views, glossary, FURPS+                                      |
| [use-cases](use-cases/README.md)               | Every user story in the distributed system (one folder each: README, SSD, SD), use case diagrams per service |
| [contracts](contracts/README.md)               | The five calls between services (C1-C5): request, answers, failures, SD                                      |

The architecture explained in one document (views, where the data lives, replication and forwarding,
consistency, fault tolerance, scenarios, security, decisions, limitations): [`../README.md`](../README.md).
Per service (logical view, components, domain model, scenarios): [aircraft-maintenance](../aircraft-maintenance/README.md),
[airports-routes](../airports-routes/README.md), [flight-ops](../flight-ops/README.md), [login](../login/README.md).

Diagram sources are in the `puml/` folder next to each `svg/` folder; `scripts/generate-svg.sh`
regenerates them all.
