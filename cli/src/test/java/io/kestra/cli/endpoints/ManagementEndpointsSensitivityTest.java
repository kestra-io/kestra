package io.kestra.cli.endpoints;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.micronaut.configuration.metrics.management.endpoint.MetricsEndpoint;
import io.micronaut.configuration.metrics.micrometer.prometheus.management.PrometheusEndpoint;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.management.endpoint.EndpointSensitivityProcessor;

import static org.assertj.core.api.Assertions.assertThat;

// Micronaut defaults /prometheus to sensitive but not /metrics, so scrapers 401 once the security filter is on (EE).
@SuppressWarnings("rawtypes")
class ManagementEndpointsSensitivityTest {
    @Test
    void metricsAndPrometheusAreNotSensitive() {
        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            Map<ExecutableMethod, Boolean> sensitivity = ctx.getBean(EndpointSensitivityProcessor.class).getEndpointMethods();

            assertThat(sensitiveFlags(sensitivity, PrometheusEndpoint.class)).isNotEmpty().containsOnly(false);
            assertThat(sensitiveFlags(sensitivity, MetricsEndpoint.class)).isNotEmpty().containsOnly(false);
        }
    }

    private static List<Boolean> sensitiveFlags(Map<ExecutableMethod, Boolean> sensitivity, Class<?> endpoint) {
        return sensitivity.entrySet().stream()
            .filter(entry -> entry.getKey().getDeclaringType().equals(endpoint))
            .map(Map.Entry::getValue)
            .toList();
    }
}
