package io.kestra.core.services;

import io.kestra.core.models.ServerType;
import io.kestra.core.models.collectors.*;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.VersionProvider;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.hateoas.JsonError;
import lombok.extern.slf4j.Slf4j;

import java.lang.management.ManagementFactory;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@Slf4j
public class CollectorService {
    private static final String UUID = IdUtils.create();

    @Inject
    @Client
    protected RxHttpClient client;

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private VersionProvider versionProvider;

    @Nullable
    @Value("${kestra.server-type}")
    protected ServerType serverType;

    @Nullable
    @Value("${kestra.url:}")
    private String kestraUrl;

    @Value("${kestra.anonymous-usage-report.uri}")
    protected URI url;

    private transient Usage.UsageBuilder<?, ?> builder;

    protected synchronized Usage.UsageBuilder<?, ?> builder() {
        boolean first = builder == null;

        if (first) {
            builder = Usage.builder()
                .startUuid(UUID)
                .serverType(serverType)
                .version(versionProvider.getVersion())
                .zoneId(ZoneId.systemDefault())
                .uri(kestraUrl == null ? null : kestraUrl)
                .environments(applicationContext.getEnvironment().getActiveNames())
                .startTime(Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime()))
                .host(HostUsage.of())
                .plugins(PluginUsage.of(applicationContext));
        }

        return builder;
    }

    public Usage metrics() {
        Usage.UsageBuilder<?, ?> builder = builder()
            .uuid(IdUtils.create());

        if (serverType == ServerType.EXECUTOR || serverType == ServerType.STANDALONE) {
            builder
                .flows(FlowUsage.of(flowRepository))
                .executions(ExecutionUsage.of(executionRepository));
        }

        return builder.build();
    }

    public void report() {
        try {
            Usage metrics = this.metrics();
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
            log.warn("Unable to report anonymous usage with body '{}'", t.getResponse().getBody(String.class), t);
        } catch (Exception t) {
            log.error("Unable to handle anonymous usage", t);
        }
    }

    private void handleResponse(Result result) {

    }

    protected MutableHttpRequest<Usage> request(Usage metrics) throws Exception {
        return HttpRequest.POST(this.url, metrics)
            .header("User-Agent", "Kestra/" + versionProvider.getVersion());
    }
}
