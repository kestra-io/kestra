package io.kestra.webserver.controllers.api;

import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.webserver.models.ai.DashboardGenerationPrompt;
import io.kestra.webserver.models.ai.FlowGenerationPrompt;
import io.kestra.webserver.services.ai.AiServiceInterface;
import io.kestra.webserver.services.ai.AiServiceManager;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.http.server.util.HttpClientAddressResolver;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Validated
@Controller("/api/v1/tenants/main")
public class TenantController {
    @Inject
    protected HttpClientAddressResolver httpClientAddressResolver;

    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Tenants"}, summary = "Make this dashboard the default for the entire tenant")
    @Post(uri = "/{id}/default-dashboard")
    public HttpResponse<Void> setTenantDefaultDashboard(
        @Parameter(description = "The tenant id") @PathVariable String id,
        @Parameter() @Body @Valid SetTenantDefaultDashboardRequest request
    ) {
        Tenant tenant = tenantRepository.findById(id).orElse(null);
        if (tenant == null) {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }
        Optional<Dashboard> existingDashboard = dashboardRepository.get(id, request.dashboardId());
        if (existingDashboard.isEmpty()) {
            return HttpResponse.status(HttpStatus.CONFLICT, "Dashboard with id '" + request.dashboardId() + "' does not exist");
        }

        Tenant updated = tenant
            .toBuilder()
            .dashboardConfig(new Tenant.DashboardConfig(request.dashboardId()))
            .build();
        tenantRepository.update(updated, tenant);
        return HttpResponse.ok();
    }

}
