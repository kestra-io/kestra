package io.kestra.core.services;

import io.kestra.core.models.collectors.Metrics;
import io.kestra.core.models.collectors.Result;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.VersionProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.scheduling.annotation.Scheduled;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@Slf4j
@Requires(property = "kestra.anonymous-usage-report.enabled", value = "true")
public class CollectorScheduler {
    @Inject
    CollectorService collectorService;

    @Client
    RxHttpClient client;

    @Inject
    VersionProvider versionProvider;

    @Value("${kestra.anonymous-usage-report.uri}")
    URI uri;

    @Scheduled(initialDelay = "${kestra.anonymous-usage-report.initial-delay}", fixedDelay = "${kestra.anonymous-usage-report.fixed-delay}")
    public void report() {
        try {
            Metrics metrics = collectorService.metrics();
            MutableHttpRequest<Metrics> post = this.request(metrics);

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
        } catch (Throwable t) {
            log.warn("Unable to report anonymous usage", t);
        }
    }

    private void handleResponse(Result result) {

    }

    protected MutableHttpRequest<Metrics> request(Metrics metrics) {
        return HttpRequest.POST(uri, metrics)
            .header("User-Agent", "Kestra/" + versionProvider.getVersion());
    }
}
