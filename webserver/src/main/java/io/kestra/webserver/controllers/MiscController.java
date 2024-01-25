package io.kestra.webserver.controllers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.models.collectors.Usage;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.TemplateRepositoryInterface;
import io.kestra.core.services.CollectorService;
import io.kestra.core.services.InstanceService;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.VersionProvider;
import io.kestra.webserver.services.BasicAuthService;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.inject.Inject;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
@Controller
public class MiscController {
    @Inject
    VersionProvider versionProvider;

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

    @Get("/ping")
    @Hidden
    public HttpResponse<?> ping() {
        return HttpResponse.ok("pong");
    }

    @Get("/api/v1{/tenant}/configs")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Misc"}, summary = "Get current configurations")
    public Configuration configuration() throws JsonProcessingException {
        Configuration.ConfigurationBuilder<?, ?> builder = Configuration
            .builder()
            .uuid(instanceService.fetch())
            .version(versionProvider.getVersion())
            .isTaskRunEnabled(executionRepository.isTaskRunEnabled())
            .isAnonymousUsageEnabled(this.isAnonymousUsageEnabled)
            .isTemplateEnabled(templateRepository.isPresent())
            .preview(Preview.builder()
                .initial(this.initialPreviewRows)
                .max(this.maxPreviewRows)
                .build()
            ).isOauthEnabled(basicAuthService.isEnabled());

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

    @Get("/api/v1{/tenant}/usages/all")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Misc"}, summary = "Get instance usage information")
    public Usage usages() {
        return collectorService.metrics();
    }

    @Getter
    @NoArgsConstructor
    @SuperBuilder(toBuilder = true)
    public static class Configuration {
        String uuid;

        String version;

        @JsonInclude
        Boolean isTaskRunEnabled;

        @JsonInclude
        Boolean isAnonymousUsageEnabled;

        @JsonInclude
        Boolean isTemplateEnabled;

        Environment environment;

        Preview preview;

        Boolean isOauthEnabled;
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
}
