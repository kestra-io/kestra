package io.kestra.webserver.controllers.api;

import io.kestra.core.exceptions.ConflictException;
import io.kestra.core.models.Setting;
import io.kestra.core.models.settings.DashboardSettings;
import io.kestra.core.repositories.DashboardRepositoryInterface;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.tenant.TenantService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller("/api/v1/tenants/main")
public class TenantController {
    @Inject
    private TenantService tenantService;
    @Inject
    private DashboardRepositoryInterface dashboardRepository;
    @Inject
    private SettingRepositoryInterface settingRepository;

    public record SetTenantDefaultDashboardsRequest(
        String defaultHomeDashboard,
        String defaultFlowOverviewDashboard,
        String defaultNamespaceOverviewDashboard) {
    }

    public static final String OSS_DASHBOARD_SETTINGS = "kestra.oss.dashboard-settings";

    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Tenants"}, summary = "Make this dashboard the default for the entire tenant")
    @Post(uri = "/settings/default-dashboards")
    public HttpResponse<DashboardSettings> setTenantDefaultDashboard(
        @Parameter() @Body @Valid SetTenantDefaultDashboardsRequest request
    ) {
        var tenantId = tenantService.resolveTenant();

        if(request.defaultHomeDashboard() != null){
            dashboardRepository.get(tenantId, request.defaultHomeDashboard())
                .orElseThrow(() -> new ConflictException("Dashboard with id '" + request.defaultHomeDashboard() + "' does not exist"));
        }
        if(request.defaultFlowOverviewDashboard() != null){
            dashboardRepository.get(tenantId, request.defaultFlowOverviewDashboard())
                .orElseThrow(() -> new ConflictException("Dashboard with id '" + request.defaultFlowOverviewDashboard() + "' does not exist"));
        }
        if(request.defaultNamespaceOverviewDashboard() != null){
            dashboardRepository.get(tenantId, request.defaultNamespaceOverviewDashboard())
                .orElseThrow(() -> new ConflictException("Dashboard with id '" + request.defaultNamespaceOverviewDashboard() + "' does not exist"));
        }

        var dashboardSettings = settingRepository.findByKey(OSS_DASHBOARD_SETTINGS)
            .map(Setting::getValue)
            .map(value -> JacksonMapper.ofJson(false).convertValue(value, DashboardSettings.class))
            .orElse(new DashboardSettings());


        DashboardSettings saved = dashboardSettings.toBuilder()
            .defaultHomeDashboard(request.defaultHomeDashboard())
            .defaultFlowOverviewDashboard(request.defaultFlowOverviewDashboard())
            .defaultNamespaceOverviewDashboard(request.defaultNamespaceOverviewDashboard())
            .build();
        settingRepository.save(Setting.builder()
            .key(OSS_DASHBOARD_SETTINGS).value(
                saved
            ).build());

        return HttpResponse.ok(saved);
    }

}
