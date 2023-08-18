package io.kestra.webserver.controllers;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.collectors.Usage;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.TemplateRepositoryInterface;
import io.kestra.core.services.CollectorService;
import io.kestra.core.services.InstanceService;
import io.kestra.core.utils.VersionProvider;
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
import lombok.Value;
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
    Optional<TemplateRepositoryInterface> templateRepository;

    @io.micronaut.context.annotation.Value("${kestra.anonymous-usage-report.enabled}")
    protected Boolean isAnonymousUsageEnabled;

    @io.micronaut.context.annotation.Value("${kestra.environment.name}")
    @Nullable
    protected String environmentName;

    @io.micronaut.context.annotation.Value("${kestra.environment.color}")
    @Nullable
    protected String environmentColor;

    @Get("/ping")
    @Hidden
    public HttpResponse<?> ping() {
        return HttpResponse.ok("pong");
    }

    @Get("/api/v1/configs")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Misc"}, summary = "Get current configurations")
    public Configuration configuration() {
        Configuration.ConfigurationBuilder builder = Configuration
            .builder()
            .uuid(instanceService.fetch())
            .version(versionProvider.getVersion())
            .isTaskRunEnabled(executionRepository.isTaskRunEnabled())
            .isAnonymousUsageEnabled(this.isAnonymousUsageEnabled)
            .isWorkerInstanceEnabled(false)
            .isTemplateEnabled(templateRepository.isPresent());

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

    @Get("/api/v1/usages")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Misc"}, summary = "Get execution usage information")
    public Usage usages() {
        return collectorService.metrics();
    }

    @Value
    @Builder(toBuilder = true)
    public static class Configuration {
        String uuid;

        String version;

        @JsonInclude
        Boolean isTaskRunEnabled;

        @JsonInclude
        Boolean isAnonymousUsageEnabled;

        @JsonInclude
        Boolean isWorkerInstanceEnabled;

        @JsonInclude
        Boolean isTemplateEnabled;

        Environment environment;
    }

    @Value
    @Builder(toBuilder = true)
    public static class Environment {
        String name;
        String color;
    }
}
