package io.kestra.core.services;

import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@Slf4j
@Requires(property = "kestra.anonymous-usage-report.enabled", value = "true")
@Requires(property = "kestra.server-type")
public class CollectorScheduler {
    @Inject
    protected CollectorService collectorService;

    @Scheduled(initialDelay = "${kestra.anonymous-usage-report.initial-delay}", fixedDelay = "${kestra.anonymous-usage-report.fixed-delay}")
    public void report() {
        collectorService.report();
    }
}
