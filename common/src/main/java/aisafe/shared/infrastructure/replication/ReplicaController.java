package aisafe.shared.infrastructure.replication;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Replication routes between replicas of the same service (role SERVICE only).
 */
@RestController
@RequestMapping("/internal/replicas")
@Tag(name = "Internal", description = "Routes used by other services, not by clients")
public class ReplicaController {

    private final Map<String, ReplicaHandler<?>> handlers;
    private final ReplicaApplier applier;
    private final ReplicaExporter exporter;

    public ReplicaController(List<ReplicaHandler<?>> handlers, ReplicaApplier applier, ReplicaExporter exporter) {
        this.handlers = handlers.stream().collect(Collectors.toMap(ReplicaHandler::type, Function.identity()));
        this.applier = applier;
        this.exporter = exporter;
    }

    @Operation(summary = "Apply a change sent by a peer", description = "Saves or deletes (tombstone) the entity, unless this replica already knows a later change of it. Idempotent.")
    @PutMapping("/{type}")
    public ResponseEntity<Void> apply(@PathVariable String type, @RequestParam String key,
                                      @RequestBody ReplicaEnvelope envelope) {
        ReplicaHandler<?> handler = handlers.get(type);
        if (handler == null) {
            return ResponseEntity.notFound().build();
        }
        if (!key.equals(envelope.key())) {
            throw new IllegalArgumentException("The key in the route and in the body differ.");
        }
        applier.apply(handler, envelope);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Export what this replica holds", description = "Live entities and tombstones, with the stamp of their last change. With 'for' (a peer recovering or syncing): catalogs, and the records that peer should hold (it is their home or the home's successor). Without it (a peer building a report): everything held here.")
    @GetMapping("/{type}")
    public ResponseEntity<List<ReplicaEnvelope>> export(@PathVariable String type,
                                                        @RequestParam(value = "for", required = false) String forNode) {
        ReplicaHandler<?> handler = handlers.get(type);
        if (handler == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(exporter.export(handler, forNode));
    }
}
