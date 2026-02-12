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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Slf4j
@Singleton
@Requires(property = "kestra.server-type", value = "WEBSERVER")
public class SharedServiceInstanceMetricService {
    private final ServiceType serverType;

    private final MetricConfig metricConfig;

    private final ServiceInstanceRepositoryInterface serviceInstanceRepository;

    private final Map<MetricKey, AtomicReference<Number>> sharedMetricsValues = new HashMap<>();

    private final Map<MetricKey, io.micrometer.core.instrument.Gauge> sharedMetricsGauges = new HashMap<>();

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

        serviceInstances.stream()
        .filter(serviceInstance -> !(serviceInstance.type() == serverType))
        .filter(serviceInstance -> metricConfig.getSharedServiceInstanceMetrics().containsKey(serviceInstance.type()))
        .forEach(serviceInstance -> serviceInstance.metrics()
            .stream()
            .filter(metric -> metricConfig.getSharedServiceInstanceMetrics().get(serviceInstance.type()).contains(metric.name()))
            .forEach(metric -> {
                    List<Metric.Tag> metricTags = new ArrayList<>(metric.tags());
                    List<String> tags = new ArrayList<>(metric.tags().stream().map(
                        (tag) -> List.of(tag.key(), tag.value())
                    ).flatMap(List::stream).toList());

                    if (metric.tags().stream().map(Metric.Tag::key).noneMatch(key -> key.equals("instance_id"))) {
                        tags.add(MetricRegistry.SERVICE_ID);
                        tags.add(serviceInstance.uid());
                        metricTags.add(new Metric.Tag(MetricRegistry.SERVICE_ID, serviceInstance.uid()));
                    }

                    MetricKey metricKey = new MetricKey(metric.name(), List.copyOf(metricTags));

                    sharedMetricsValues.computeIfAbsent(
                        metricKey, k -> new AtomicReference<>()
                    ).set(metric.value());

                sharedMetricsGauges.computeIfAbsent(metricKey, (mk) -> metricRegistry.gauge(
                    metric.name(),
                    metric.description(),
                    (Supplier<Number>) () -> sharedMetricsValues.get(metricKey).get(),
                    tags.toArray(new String[0])
                ));
            })
        );
        cleanUp(serviceInstances);
    }

    private void cleanUp(List<ServiceInstance> serviceInstances) {
        List<String> serviceInstanceIds = serviceInstances.stream().map(ServiceInstance::uid).toList();
        List<MetricKey> toRemove = sharedMetricsValues.keySet().stream()
            .filter(metricKey -> metricKey.tags().stream()
                .filter(tag -> tag.key().equals(MetricRegistry.SERVICE_ID))
                .findFirst()
                .map(tag -> !serviceInstanceIds.contains(tag.value()))
                .orElse(false))
            .toList();

        toRemove.forEach(metricKey -> {
            log.debug("Removing metric {} from shared metrics, as the associated service instance is no longer active", metricKey);
            metricRegistry.removeMeter(sharedMetricsGauges.remove(metricKey));
            sharedMetricsValues.remove(metricKey);
        });
    }

    private record MetricKey (
        String name, List<Metric.Tag> tags
    ) {
        public static MetricKey of(Metric metric) {
            return new MetricKey(metric.name(), List.copyOf(metric.tags()));
        }
    }
}
