package aisafe.routes.infrastructure;

import aisafe.routes.application.ViewRouteForServicesUseCase;
import aisafe.routes.application.dtos.RouteForServicesResponse;
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
 * Internal route routes, used by other services and by the peers of this service.
 * Plain JSON, without HATEOAS links.
 */
@RestController
@RequestMapping("/internal/routes")
@Tag(name = "Internal", description = "Routes used by other services, not by clients")
public class RouteInternalController {

    private final ViewRouteForServicesUseCase viewRouteForServices;

    public RouteInternalController(ViewRouteForServicesUseCase viewRouteForServices) {
        this.viewRouteForServices = viewRouteForServices;
    }

    @Operation(summary = "Get a route", description = "Status, minimum capacity and range, flight time and distance in km of a route, wherever it is held: a replica that does not hold it asks its peers. Used by flight-ops to schedule flights. (C2)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Route found"),
            @ApiResponse(responseCode = "404", description = "No replica holds this route")
    })
    @GetMapping("/{origin}/{destination}")
    public ResponseEntity<RouteForServicesResponse> getRoute(@PathVariable String origin, @PathVariable String destination) {
        return ResponseEntity.ok(viewRouteForServices.execute(origin, destination));
    }
}
