package io.kestra.webserver.services;

import io.kestra.core.metrics.MetricConfig;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.repositories.ServiceInstanceRepositoryInterface;
import io.kestra.core.server.Metric;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceType;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.data.model.Pageable;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Singleton
@Requires(property = "kestra.server-type", value = "WEBSERVER")
public class SharedServiceInstanceMetricService {
    private final ServiceType serverType;

    private final MetricConfig metricConfig;

    private final ServiceInstanceRepositoryInterface serviceInstanceRepository;

    private final Map<MetricKey, AtomicReference<Number>> sharedMetricsValues = new ConcurrentHashMap<>();

    private final Map<MetricKey, io.micrometer.core.instrument.Gauge> sharedMetricsGauges = new ConcurrentHashMap<>();

    private final MetricRegistry metricRegistry;

    public SharedServiceInstanceMetricService(
        @Value("${kestra.server-type}")
        ServiceType serverType,
        MetricConfig metricConfig,
        ServiceInstanceRepositoryInterface serviceInstanceRepository, MetricRegistry metricRegistry
    ) {
        this.serverType = serverType;
        this.metricConfig = metricConfig;
        this.serviceInstanceRepository = serviceInstanceRepository;
        this.metricRegistry = metricRegistry;
    }

    @Scheduled(fixedDelay = "30s")
    void populateSharedServiceInstanceMetrics() {
        List<ServiceInstance> serviceInstances = serviceInstanceRepository.find(
            Pageable.unpaged(),
            Service.ServiceState.allRunningStates(),
            metricConfig.getSharedServiceInstanceMetrics().keySet()
        );

       var sumedServiceInstanceMetrics =  serviceInstances.stream()
        .filter(serviceInstance -> !(serviceInstance.type() == serverType))
        .filter(serviceInstance -> metricConfig.getSharedServiceInstanceMetrics().containsKey(serviceInstance.type()))
        .flatMap(serviceInstance -> serviceInstance.metrics()
            .stream()
            .filter(metric ->
                metricConfig.getSharedServiceInstanceMetrics().get(serviceInstance.type()).contains(metric.name())
            )
        ).collect(
            Collectors.groupingBy(
                (metric) -> new MetricKey(metric.name(), metric.description(), metric.tags()),
                Collectors.summingDouble((metric) -> metric.value().doubleValue())
            )
       );

        sumedServiceInstanceMetrics.forEach((metricKey, value) -> {
            sharedMetricsValues.computeIfAbsent(metricKey, k -> new AtomicReference<>()).set(value);

            List<String> tags = metricKey.tags().stream().map(
                        (tag) -> List.of(tag.key(), tag.value())
                    ).flatMap(List::stream).toList();

            sharedMetricsGauges.computeIfAbsent(metricKey, (mk) -> metricRegistry.gauge(
                removeMetricPrefix(metricKey.name()),
                metricKey.description(),
                (Supplier<Number>) () -> sharedMetricsValues.get(metricKey).get(),
                tags.toArray(new String[0])
            ));
        });

        cleanUp(sumedServiceInstanceMetrics.keySet());
    }

    private void cleanUp(Set<MetricKey> metricKeys) {
        sharedMetricsValues.forEach((metricKey, value) -> {
            log.debug("Removing metric {} from shared metrics, as the associated service instance is no longer active", metricKey);
            if (!metricKeys.contains(metricKey)) {
                value.set(0);
            }
        });
    }

    private String removeMetricPrefix(String metricName) {
        String prefix = metricConfig.getPrefix() + ".";
        return metricName.startsWith(prefix) ? metricName.substring(prefix.length()) : metricName;
    }

    private record MetricKey (
        String name, String description, List<Metric.Tag> tags
    ) {
    }
}
