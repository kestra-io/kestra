package io.kestra.core.services;

import io.kestra.core.models.ServerType;
import io.kestra.core.models.collectors.*;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.VersionProvider;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.time.ZoneId;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@Slf4j
public class CollectorService {
    private static final String UUID = IdUtils.create();

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Nullable
    @Value("${kestra.server-type}")
    protected ServerType serverType;

    @Nullable
    @Value("${kestra.url:}")
    private String uri;

    @Inject
    private VersionProvider versionProvider;

    private transient Metrics.MetricsBuilder<?, ?> builder;

    protected synchronized Metrics.MetricsBuilder<?, ?> builder() {
        boolean first = builder == null;

        if (first) {
            builder = Metrics.builder()
                .uuid(UUID)
                .version(versionProvider.getVersion())
                .zoneId(ZoneId.systemDefault())
                .uri(uri == null ? null : IdUtils.from(uri))
                .environments(applicationContext.getEnvironment().getActiveNames())
                .startTime(Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime()));
        }

        if (first) {
            builder
                .host(HostMetrics.of())
                .pluginMetrics(PluginMetrics.of(applicationContext));
        }

        return builder;
    }

    public Metrics metrics() {
        Metrics.MetricsBuilder<?, ?> builder = builder()
            .serverType(serverType);

        if (serverType == ServerType.EXECUTOR || serverType == ServerType.STANDALONE) {
            builder
                .flowMetrics(FlowMetrics.of(flowRepository))
                .executionMetrics(ExecutionMetrics.of(executionRepository));
        }

        return builder.build();
    }
}
