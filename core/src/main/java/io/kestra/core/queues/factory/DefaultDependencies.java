package io.kestra.core.queues.factory;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.queues.QueueService;
import io.kestra.core.services.IgnoreExecutionService;
import io.kestra.core.utils.ExecutorsUtils;

import io.micrometer.observation.ObservationRegistry;
import io.micronaut.context.annotation.Secondary;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Secondary
@Singleton
public record DefaultDependencies(
    @Inject QueueService queueService,
    @Inject ExecutorsUtils executorsUtils,
    @Inject MetricRegistry metricRegistry,
    @Inject ObservationRegistry observationRegistry,
    @Inject IgnoreExecutionService ignoreExecutionService,
    @Inject ModelValidator modelValidator) implements QueueBackendDependencies {
}