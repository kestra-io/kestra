package io.kestra.webserver.services;

import io.kestra.core.metrics.MetricConfig;
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

@Slf4j
@Singleton
@Requires(property = "kestra.server-type", value = "WEBSERVER")
public class SharedServiceInstanceMetricService {
    private final ServiceType serverType;

    private final MetricConfig metricConfig;

    private final ServiceInstanceRepositoryInterface serviceInstanceRepository;

    private final Map<String, Map<MetricKey, Metric>> sharedMetrics = new HashMap<>();

    public SharedServiceInstanceMetricService(
        @Value("${kestra.server-type}")
        ServiceType serverType,
        MetricConfig metricConfig,
        ServiceInstanceRepositoryInterface serviceInstanceRepository
    ) {
        this.serverType = serverType;
        this.metricConfig = metricConfig;
        this.serviceInstanceRepository = serviceInstanceRepository;
    }

    public Set<String> getMetricNames() {
        return sharedMetrics.keySet();
    }

    public List<Metric> getMetric(String name) {
        return sharedMetrics.getOrDefault(name, Map.of()).values().stream().toList();
    }

    @Scheduled(fixedDelay = "30s")
    void populateSharedServiceInstanceMetrics() {
        ArrayList<ServiceInstance> serviceInstances = serviceInstanceRepository.find(
            Pageable.unpaged(),
            Service.ServiceState.allRunningStates(),
            metricConfig.getSharedServiceInstanceMetrics().keySet()
        );

        serviceInstances.stream()
        .filter(serviceInstance -> !(serviceInstance.type() == serverType))
        .forEach(serviceInstance -> serviceInstance.metrics()
            .stream()
            .filter(metric -> metricConfig.getSharedServiceInstanceMetrics().get(serviceInstance.type()) != null)
            .filter(metric -> metricConfig.getSharedServiceInstanceMetrics().get(serviceInstance.type()).contains(metric.name()))
            .forEach(metric ->
                sharedMetrics.computeIfAbsent(metric.name(), k -> new HashMap<>()).put(
                    MetricKey.of(metric), metric
                )
            )
        );
    }

    private record MetricKey (
        String name, List<Metric.Tag> tags
    ) {
        public static MetricKey of(Metric metric) {
            return new MetricKey(metric.name(), metric.tags());
        }
    }
}
