package aisafe.flightcopies.infrastructure;

import aisafe.flightcopies.application.RecordFlightCopyUseCase;
import aisafe.flightcopies.application.dtos.FlightCopyResponse;
import aisafe.flightcopies.application.dtos.RecordFlightCopyRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Internal route used by flight-ops to send the copy of each flight (contract C4).
 */
@RestController
@RequestMapping("/internal/flights")
@Tag(name = "Internal", description = "Routes used by other services, not by clients")
public class FlightCopyController {

    private final RecordFlightCopyUseCase recordFlightCopy;

    public FlightCopyController(RecordFlightCopyUseCase recordFlightCopy) {
        this.recordFlightCopy = recordFlightCopy;
    }

    @Operation(summary = "Store the copy of a flight", description = "Command sent by flight-ops whenever a flight is created or changed. Idempotent: a repeated or older copy changes nothing. For a new future flight, it is the 'reserve the aircraft' step of the scheduling Saga. (C4)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Copy stored (or an equal or newer one was already stored)"),
            @ApiResponse(responseCode = "400", description = "Invalid copy"),
            @ApiResponse(responseCode = "404", description = "The aircraft does not exist"),
            @ApiResponse(responseCode = "409", description = "The aircraft cannot take a new flight")
    })
    @PutMapping("/{id}")
    public ResponseEntity<FlightCopyResponse> putFlightCopy(@PathVariable String id,
                                                            @Valid @RequestBody RecordFlightCopyRequest body) {
        if (!id.equals(body.id())) {
            throw new IllegalArgumentException("The flight id in the path and in the body differ.");
        }
        return ResponseEntity.ok(recordFlightCopy.execute(body));
    }
}
