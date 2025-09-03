package io.kestra.webserver.controllers.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.collectors.ExecutionUsage;
import io.kestra.core.models.collectors.FlowUsage;
import io.kestra.core.reporter.Reportable;
import io.kestra.core.reporter.reports.FeatureUsageReport;
import io.kestra.core.repositories.DashboardRepositoryInterface;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.TemplateRepositoryInterface;
import io.kestra.core.services.InstanceService;
import io.kestra.core.utils.NamespaceUtils;
import io.kestra.core.utils.VersionProvider;
import io.kestra.webserver.services.BasicAuthService;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Controller("/api/v1")
public class MiscController {
    @Inject
    protected ApplicationContext applicationContext;

    @Inject
    VersionProvider versionProvider;

    @Inject
    DashboardRepositoryInterface dashboardRepository;

    @Inject
    ExecutionRepositoryInterface executionRepository;

    @Inject
    InstanceService instanceService;

    @Inject
    FeatureUsageReport featureUsageReport;

    @Inject
    BasicAuthService basicAuthService;

    @Inject
    Optional<TemplateRepositoryInterface> templateRepository;

    @Inject
    NamespaceUtils namespaceUtils;

    @io.micronaut.context.annotation.Value("${kestra.anonymous-usage-report.enabled}")
    protected Boolean isAnonymousUsageEnabled;

    @io.micronaut.context.annotation.Value("${kestra.ui-anonymous-usage-report.enabled:false}")
    protected Boolean isUiAnonymousUsageEnabled;

    @io.micronaut.context.annotation.Value("${kestra.environment.name}")
    @Nullable
    protected String environmentName;

    @io.micronaut.context.annotation.Value("${kestra.environment.color}")
    @Nullable
    protected String environmentColor;

    @io.micronaut.context.annotation.Value("${kestra.url}")
    @Nullable
    protected String kestraUrl;

    @io.micronaut.context.annotation.Value("${kestra.server.preview.initial-rows:100}")
    private Integer initialPreviewRows;

    @io.micronaut.context.annotation.Value("${kestra.server.preview.max-rows:5000}")
    private Integer maxPreviewRows;

    @io.micronaut.context.annotation.Value("${kestra.hidden-labels.prefixes:}")
    private List<String> hiddenLabelsPrefixes;


    @Get("/configs")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Misc"}, summary = "Retrieve the instance configuration.", description = "Global endpoint available to all users.")
    public Configuration getConfiguration() throws JsonProcessingException { // JsonProcessingException might be thrown in EE
        Configuration.ConfigurationBuilder<?, ?> builder = Configuration
            .builder()
            .uuid(instanceService.fetch())
            .edition(Edition.OSS)
            .version(versionProvider.getVersion())
            .commitId(versionProvider.getRevision())
            .commitDate(versionProvider.getDate())
            .isCustomDashboardsEnabled(dashboardRepository.isEnabled())
            .isTaskRunEnabled(executionRepository.isTaskRunEnabled())
            .isAnonymousUsageEnabled(this.isAnonymousUsageEnabled)
            .isUiAnonymousUsageEnabled(this.isUiAnonymousUsageEnabled)
            .isTemplateEnabled(templateRepository.isPresent())
            .preview(Preview.builder()
                .initial(this.initialPreviewRows)
                .max(this.maxPreviewRows)
                .build())
            .isAiEnabled(applicationContext.containsBean(AiController.class))
            .isBasicAuthInitialized(basicAuthService.isBasicAuthInitialized())
            .systemNamespace(namespaceUtils.getSystemFlowNamespace())
            .resourceToFilters(QueryFilter.Resource.asResourceList())
            .hiddenLabelsPrefixes(hiddenLabelsPrefixes)
            .url(kestraUrl);

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

    public enum Edition {
        OSS,
        EE
    }

    @Get("/{tenant}/usages/all")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Misc"}, summary = "Retrieve instance usage information")
    public ApiUsage getUsages() {
        ZonedDateTime now = ZonedDateTime.now();
        FeatureUsageReport.UsageEvent event = featureUsageReport.report(now.toInstant(), Reportable.TimeInterval.of(now.minus(Duration.ofDays(1)), now));
        return ApiUsage.builder()
            .flows(event.getFlows())
            .executions(event.getExecutions())
            .build();
    }

    @Post(uri = "/{tenant}/basicAuth")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Misc"}, summary = "Configure basic authentication for the instance.", description = "Sets up basic authentication credentials.")
    public HttpResponse<Void> createBasicAuth(
        @RequestBody(description = "") @Body BasicAuthCredentials basicAuthCredentials
    ) {
        basicAuthService.save(basicAuthCredentials.getUid(), new BasicAuthService.BasicAuthConfiguration(basicAuthCredentials.getUsername(), basicAuthCredentials.getPassword()));

        return HttpResponse.noContent();
    }


    @Get("/basicAuthValidationErrors")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Misc"}, summary = "Retrieve the instance configuration.", description = "Global endpoint available to all users.")
    public List<String> getBasicAuthConfigErrors() {
        return basicAuthService.validationErrors();
    }

    @Getter
    @NoArgsConstructor
    @SuperBuilder(toBuilder = true)
    public static class Configuration {
        String uuid;

        String version;

        Edition edition;

        String commitId;

        ZonedDateTime commitDate;

        @JsonInclude
        Boolean isCustomDashboardsEnabled;

        @JsonInclude
        Boolean isTaskRunEnabled;

        @JsonInclude
        Boolean isAnonymousUsageEnabled;

        @JsonInclude
        Boolean isUiAnonymousUsageEnabled;

        @JsonInclude
        Boolean isTemplateEnabled;

        Environment environment;

        String url;

        Preview preview;

        String systemNamespace;

        List<String> hiddenLabelsPrefixes;
        // List of filter by component
        List<QueryFilter.ResourceField> resourceToFilters;

        Boolean isAiEnabled;

        Boolean isBasicAuthInitialized;
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

    @SuperBuilder(toBuilder = true)
    @Getter
    public static class ApiUsage {
        private FlowUsage flows;
        private ExecutionUsage executions;
    }
}
