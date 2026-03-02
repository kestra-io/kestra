package io.kestra.webserver.controllers.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.services.MaintenanceService;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.inject.Inject;
import lombok.Builder;
import lombok.Value;

import java.time.ZonedDateTime;

/**
 * Controller for instance-level system information and status.
 */
@Controller("/api/v1/instance")
public class InstanceController {

    @Inject
    private MaintenanceService maintenanceService;

    /**
     * Get maintenance mode status.
     * This endpoint is publicly accessible to allow monitoring tools and status pages
     * to check system availability without requiring authentication.
     *
     * @return maintenance status information
     */
    @Get("/maintenance/status")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = {"Instance"},
        summary = "Get maintenance mode status",
        description = "Retrieves the current maintenance mode status. This is a public endpoint available without authentication."
    )
    public MaintenanceStatusResponse getMaintenanceStatus() {
        MaintenanceService.Status status = maintenanceService.getStatus();
        
        return MaintenanceStatusResponse.builder()
            .status(status.isActive() ? "maintenance" : "operational")
            .maintenanceDetails(status.isActive() ? MaintenanceDetails.builder()
                .isActive(status.isActive())
                .reason(status.getReason())
                .startTime(status.getStartTime())
                .estimatedEnd(status.getEstimatedEnd())
                .build() : null)
            .build();
    }

    /**
     * Response model for maintenance status.
     */
    @Value
    @Builder(toBuilder = true)
    public static class MaintenanceStatusResponse {
        /**
         * Overall status: "operational" or "maintenance"
         */
        String status;

        /**
         * Detailed maintenance information (only present when in maintenance mode)
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        MaintenanceDetails maintenanceDetails;
    }

    /**
     * Detailed maintenance information.
     */
    @Value
    @Builder(toBuilder = true)
    public static class MaintenanceDetails {
        /**
         * Whether maintenance mode is currently active
         */
        boolean isActive;

        /**
         * Reason for the maintenance
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String reason;

        /**
         * Time when maintenance mode was activated
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        ZonedDateTime startTime;

        /**
         * Estimated time when maintenance will be completed
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        ZonedDateTime estimatedEnd;
    }
}
