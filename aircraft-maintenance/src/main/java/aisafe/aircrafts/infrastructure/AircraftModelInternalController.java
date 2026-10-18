package aisafe.aircrafts.infrastructure;

import aisafe.aircrafts.application.ViewAircraftModelForServicesUseCase;
import aisafe.aircrafts.application.dtos.AircraftModelForServicesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/aircraft-models")
@Tag(name = "Internal", description = "Routes used by other services, not by clients")
public class AircraftModelInternalController {

    private final ViewAircraftModelForServicesUseCase viewAircraftModelForServices;

    public AircraftModelInternalController(ViewAircraftModelForServicesUseCase viewAircraftModelForServices) {
        this.viewAircraftModelForServices = viewAircraftModelForServices;
    }

    @Operation(summary = "Get an aircraft model", description = "Whether an aircraft model exists, with its manufacturer. Used by airports-routes to certify airports only for existing models (US106a). (C3)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aircraft model found"),
            @ApiResponse(responseCode = "404", description = "No such aircraft model")
    })
    @GetMapping("/{modelName}")
    public ResponseEntity<AircraftModelForServicesResponse> getAircraftModel(@PathVariable String modelName) {
        return ResponseEntity.ok(viewAircraftModelForServices.execute(modelName));
    }
}
