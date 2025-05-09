package io.kestra.core.services;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.ServerType;
import io.kestra.core.models.collectors.*;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.ServiceInstanceRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.VersionProvider;
import io.micrometer.core.instrument.Timer;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.lang.management.ManagementFactory;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Singleton
@Slf4j
public class CollectorService {
    protected static final String UUID = IdUtils.create();

    @Inject
    @Client
    protected ReactorHttpClient client;

    @Inject
    protected ApplicationContext applicationContext;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    protected InstanceService instanceService;

    @Inject
    protected VersionProvider versionProvider;

    @Inject
    protected PluginRegistry pluginRegistry;

    @Nullable
    @Value("${kestra.server-type}")
    protected ServerType serverType;

    @Nullable
    @Value("${kestra.url:}")
    protected String kestraUrl;

    @Value("${kestra.anonymous-usage-report.uri}")
    protected URI url;

    @Inject
    private ServiceInstanceRepositoryInterface serviceRepository;

    @Inject
    private MetricRegistry metricRegistry;

    private transient Usage defaultUsage;

    protected synchronized Usage defaultUsage() {
        boolean first = defaultUsage == null;

        if (first) {
            defaultUsage = Usage.builder()
                .startUuid(UUID)
                .instanceUuid(instanceService.fetch())
                .serverType(serverType)
                .version(versionProvider.getVersion())
                .zoneId(ZoneId.systemDefault())
                .uri(kestraUrl == null ? null : kestraUrl)
                .environments(applicationContext.getEnvironment().getActiveNames())
                .startTime(Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime()))
                .host(HostUsage.of())
                .configurations(ConfigurationUsage.of(applicationContext))
                .plugins(PluginUsage.of(pluginRegistry))
                .build();
        }

        return defaultUsage;
    }

    public Usage metrics(boolean details) {
        return metrics(details, serverType == ServerType.WORKER || serverType == ServerType.SCHEDULER || serverType == ServerType.STANDALONE);
    }

    public Usage metrics(boolean details, boolean metrics) {
        ZonedDateTime to = ZonedDateTime.now();

        ZonedDateTime from = to
            .toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
            .minusDays(1);

        return metrics(details, metrics, from, to);
    }

    public Usage metrics(boolean details, boolean metrics, ZonedDateTime from, ZonedDateTime to) {
        Usage.UsageBuilder<?, ?> builder = defaultUsage()
            .toBuilder()
            .uuid(IdUtils.create());

        if (details) {
            builder = builder
                .flows(FlowUsage.of(flowRepository))
                .executions(ExecutionUsage.of(executionRepository, from, to))
                .services(ServiceUsage.of(from.toInstant(), to.toInstant(), serviceRepository, Duration.ofMinutes(5)));
        }

        if (metrics) {
            builder = builder.pluginMetrics(pluginMetrics());
        }

        return builder.build();
    }

    public void report() {
        try {
            Usage metrics = this.metrics(serverType == ServerType.EXECUTOR || serverType == ServerType.STANDALONE);
            MutableHttpRequest<Usage> post = this.request(metrics);

            if (log.isTraceEnabled()) {
                log.trace("Report anonymous usage: '{}'", JacksonMapper.ofJson().writeValueAsString(metrics));
            }

            Result result = client.toBlocking()
                .retrieve(
                    post,
                    Argument.of(Result.class),
                    Argument.of(JsonError.class)
                );
            this.handleResponse(result);
        } catch (HttpClientResponseException t) {
            log.debug("Unable to report anonymous usage with body '{}'", t.getResponse().getBody(String.class), t);
        } catch (Exception t) {
            log.debug("Unable to handle anonymous usage", t);
        }
    }

    private void handleResponse(Result result) {

    }

    protected MutableHttpRequest<Usage> request(Usage metrics) throws Exception {
        return HttpRequest.POST(this.url, metrics)
            .header("User-Agent", "Kestra/" + versionProvider.getVersion());
    }

    private List<PluginMetric> pluginMetrics() {
        List<PluginMetric> taskMetrics = pluginRegistry.plugins().stream()
            .flatMap(registeredPlugin -> registeredPlugin.getTasks().stream())
            .map(cls -> cls.getName())
            .map(type -> taskMetric(type))
            .filter(opt -> opt.isPresent())
            .map(opt -> opt.get())
            .toList();

        List<PluginMetric> triggerMetrics = pluginRegistry.plugins().stream()
            .flatMap(registeredPlugin -> registeredPlugin.getTriggers().stream())
            .map(cls -> cls.getName())
            .map(type -> triggerMetric(type))
            .filter(opt -> opt.isPresent())
            .map(opt -> opt.get())
            .toList();

        return ListUtils.concat(taskMetrics, triggerMetrics);
    }

    private Optional<PluginMetric> taskMetric(String type) {
        Timer duration = metricRegistry.find(MetricRegistry.METRIC_WORKER_ENDED_DURATION).tag(MetricRegistry.TAG_TASK_TYPE, type).timer();
        return fromTimer(type, duration);
    }

    private Optional<PluginMetric> triggerMetric(String type) {
        Timer duration = metricRegistry.find(MetricRegistry.METRIC_WORKER_TRIGGER_DURATION).tag(MetricRegistry.TAG_TRIGGER_TYPE, type).timer();

        if (duration == null) {
            // this may be because this is a trigger executed by the scheduler, we search there instead
            duration = metricRegistry.find(MetricRegistry.METRIC_SCHEDULER_TRIGGER_EVALUATION_DURATION).tag(MetricRegistry.TAG_TRIGGER_TYPE, type).timer();
        }
        return fromTimer(type, duration);
    }

    private Optional<PluginMetric> fromTimer(String type, Timer timer) {
        if (timer == null || timer.count() == 0) {
            return Optional.empty();
        }

        double count = timer.count();
        double totalTime = timer.totalTime(TimeUnit.MILLISECONDS);
        double meanTime = timer.mean(TimeUnit.MILLISECONDS);

        return Optional.of(new PluginMetric(type, count, totalTime, meanTime));
    }
}
