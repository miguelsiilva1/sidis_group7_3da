package aisafe.aircrafts.infrastructure;

import aisafe.aircrafts.application.ViewAircraftForServicesUseCase;
import aisafe.aircrafts.application.dtos.AircraftForServicesResponse;
import aisafe.aircrafts.application.dtos.ViewAircraftDetailsRequest;
import aisafe.aircrafts.domain.RegistrationNumber;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal aircraft routes, used by other services and by the peers of this service.
 * Plain JSON, without HATEOAS links.
 */
@RestController
@RequestMapping("/internal/aircrafts")
@Tag(name = "Internal", description = "Routes used by other services, not by clients")
public class AircraftInternalController {

    private final ViewAircraftForServicesUseCase viewAircraftForServices;

    public AircraftInternalController(ViewAircraftForServicesUseCase viewAircraftForServices) {
        this.viewAircraftForServices = viewAircraftForServices;
    }

    @Operation(summary = "Get an aircraft", description = "Status, capacity, range, fuel consumption and model of an aircraft, wherever it is held: a replica that does not hold it asks its peers. Used by flight-ops and airports-routes. (C1)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aircraft found"),
            @ApiResponse(responseCode = "404", description = "No replica holds this aircraft")
    })
    @GetMapping("/{registration}")
    public ResponseEntity<AircraftForServicesResponse> getAircraft(@PathVariable String registration) {
        return ResponseEntity.ok(viewAircraftForServices.execute(
                new ViewAircraftDetailsRequest(new RegistrationNumber(registration))));
    }
}
