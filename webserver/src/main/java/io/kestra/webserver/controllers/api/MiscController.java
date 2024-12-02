package io.kestra.webserver.controllers.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.models.collectors.Usage;
import io.kestra.core.repositories.DashboardRepositoryInterface;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.TemplateRepositoryInterface;
import io.kestra.core.services.CollectorService;
import io.kestra.core.services.InstanceService;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.NamespaceUtils;
import io.kestra.core.utils.VersionProvider;
import io.kestra.webserver.services.BasicAuthService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.inject.Inject;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Controller("/api/v1")
public class MiscController {
    @Inject
    VersionProvider versionProvider;

    @Inject
    DashboardRepositoryInterface dashboardRepository;

    @Inject
    ExecutionRepositoryInterface executionRepository;

    @Inject
    InstanceService instanceService;

    @Inject
    CollectorService collectorService;

    @Inject
    BasicAuthService basicAuthService;

    @Inject
    Optional<TemplateRepositoryInterface> templateRepository;

    @Inject
    TenantService tenantService;

    @Inject
    NamespaceUtils namespaceUtils;

    @io.micronaut.context.annotation.Value("${kestra.anonymous-usage-report.enabled}")
    protected Boolean isAnonymousUsageEnabled;

    @io.micronaut.context.annotation.Value("${kestra.environment.name}")
    @Nullable
    protected String environmentName;

    @io.micronaut.context.annotation.Value("${kestra.environment.color}")
    @Nullable
    protected String environmentColor;

    @io.micronaut.context.annotation.Value("${kestra.server.preview.initial-rows:100}")
    private Integer initialPreviewRows;

    @io.micronaut.context.annotation.Value("${kestra.server.preview.max-rows:5000}")
    private Integer maxPreviewRows;

    @io.micronaut.context.annotation.Value("${kestra.hidden-labels.prefixes:}")
    private List<String> hiddenLabelsPrefixes;


    @Get("{/tenant}/configs")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Misc"}, summary = "Get current configurations")
    public Configuration configuration() throws JsonProcessingException {
        Configuration.ConfigurationBuilder<?, ?> builder = Configuration
            .builder()
            .uuid(instanceService.fetch())
            .version(versionProvider.getVersion())
            .commitId(versionProvider.getRevision())
            .commitDate(versionProvider.getDate())
            .isCustomDashboardsEnabled(dashboardRepository.isEnabled())
            .isTaskRunEnabled(executionRepository.isTaskRunEnabled())
            .isAnonymousUsageEnabled(this.isAnonymousUsageEnabled)
            .isTemplateEnabled(templateRepository.isPresent())
            .preview(Preview.builder()
                .initial(this.initialPreviewRows)
                .max(this.maxPreviewRows)
                .build()
            ).isBasicAuthEnabled(basicAuthService.isEnabled())
            .systemNamespace(namespaceUtils.getSystemFlowNamespace())
            .hiddenLabelsPrefixes(hiddenLabelsPrefixes);

        if (this.environmentName != null || this.environmentColor != null) {
            builder.environment(
                Environment.builder()
                    .name(this.environmentName)
                    .color(this.environmentColor)
                    .build()
            );
        }

        return builder.build();
    }

    @Get("{/tenant}/usages/all")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Misc"}, summary = "Get instance usage information")
    public Usage usages() {
        return collectorService.metrics(true);
    }

    @Post(uri = "{/tenant}/basicAuth")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Misc"}, summary = "Add basic auth to current instance")
    public HttpResponse<Void> addBasicAuth(
        @Body BasicAuthCredentials basicAuthCredentials
    ) {
        basicAuthService.save(basicAuthCredentials.getUid(), new BasicAuthService.BasicAuthConfiguration(basicAuthCredentials.getUsername(), basicAuthCredentials.getPassword()));

        return HttpResponse.noContent();
    }

    @Getter
    @NoArgsConstructor
    @SuperBuilder(toBuilder = true)
    public static class Configuration {
        String uuid;

        String version;

        String commitId;

        ZonedDateTime commitDate;

        @JsonInclude
        Boolean isCustomDashboardsEnabled;

        @JsonInclude
        Boolean isTaskRunEnabled;

        @JsonInclude
        Boolean isAnonymousUsageEnabled;

        @JsonInclude
        Boolean isTemplateEnabled;

        Environment environment;

        Preview preview;

        Boolean isBasicAuthEnabled;

        String systemNamespace;

        List<String> hiddenLabelsPrefixes;
    }

    @Value
    @Builder(toBuilder = true)
    public static class Environment {
        String name;
        String color;
    }

    @Value
    @Builder(toBuilder = true)
    public static class Preview {
        Integer initial;
        Integer max;
    }

    @Getter
    @AllArgsConstructor
    public static class BasicAuthCredentials {
        private String uid;
        private String username;
        private String password;
    }
}
