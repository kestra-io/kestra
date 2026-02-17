package io.kestra.webserver.services;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.metrics.MetricConfig;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.metrics.Gauge;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.ServiceInstanceRepositoryInterface;
import io.kestra.core.server.Metric;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceType;
import io.kestra.core.utils.IdUtils;
import io.micrometer.core.instrument.Tag;
import io.micronaut.data.model.Pageable;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.*;

@KestraTest()
class SharedServiceInstanceMetricServiceTest {
    private static final MetricConfig metricConfig = new MetricConfig(
        "kestra",
        Map.of(),
        List.of(),
        Map.of(
            ServiceType.WORKER,
            Set.of("metric-name", "kestra.metric-name")
        )
    );
    private ServiceInstanceRepositoryInterface serviceInstanceRepositoryInterface;

    @Inject
    private MetricRegistry metricRegistry;

    private SharedServiceInstanceMetricService sharedServiceInstanceMetricService;

    @BeforeEach
    void setUp() {
        serviceInstanceRepositoryInterface = Mockito.mock(ServiceInstanceRepositoryInterface.class);
        sharedServiceInstanceMetricService = new SharedServiceInstanceMetricService(
            ServiceType.WEBSERVER,
            metricConfig,
            serviceInstanceRepositoryInterface,
            metricRegistry
        );
        metricRegistry.find("metric-name").meters().forEach(metricRegistry::removeMeter);
    }

    @Test
    void shouldRegisterSeparateGauges_givenMetricsWithDifferentTags_whenPolled() {
        //Given
        Metric metricWithOutTags = buildMetric("metric-name", 1, new Metric.Tag("tag-key", "tag-value-one"));
        Metric metricWithTags = buildMetric("metric-name", 2, new Metric.Tag("tag-key", "tag-value-two"));

        ServiceInstance serviceInstance = buildServiceInstanceWithMetric(metricWithOutTags, metricWithTags);
        mockServiceInstance(List.of(serviceInstance));

        // When
        sharedServiceInstanceMetricService.populateSharedServiceInstanceMetrics();


        // Then
        Optional<io.micrometer.core.instrument.Gauge> gaugeWithoutMetricNameTag = getGaugeWithTags("metric-name", Map.of("tag-key", "tag-value-one") );
        Optional<io.micrometer.core.instrument.Gauge> gaugeWithMetricNameTags = getGaugeWithTags("metric-name", Map.of("tag-key", "tag-value-two"));

        assertThat(gaugeWithoutMetricNameTag).isPresent();
        assertThat(gaugeWithMetricNameTags).isPresent();

        assertThat(gaugeWithoutMetricNameTag.get().value()).isEqualTo(1);
        assertThat(gaugeWithMetricNameTags.get().value()).isEqualTo(2);
    }

    @Test
    void shouldReflectLatestValue_givenMetricValueChanged_whenPolledAgain() {
        // Given
        String serviceInstanceId = IdUtils.create();
        Metric metric = buildMetric("metric-name", 1);
        Metric metricUpdated = buildMetric("metric-name", 2);

        ServiceInstance serviceInstanceBeforeUpdate = buildServiceInstanceWithMetric(serviceInstanceId, metric);
        ServiceInstance serviceInstanceAfterUpdate = buildServiceInstanceWithMetric(serviceInstanceId, metricUpdated);


        // When
        mockServiceInstance(List.of(serviceInstanceBeforeUpdate));
        sharedServiceInstanceMetricService.populateSharedServiceInstanceMetrics();

        mockServiceInstance(List.of(serviceInstanceAfterUpdate));
        sharedServiceInstanceMetricService.populateSharedServiceInstanceMetrics();

        // Then
        Optional<io.micrometer.core.instrument.Gauge> gauge = getGauge("metric-name");

        assertThat(gauge).isPresent();
        assertThat(gauge.get().value()).isEqualTo(2);
    }


    @Test
    void shouldSumMetricValues_givenMultipleServiceInstances_whenReportingSameMetric() {
        // Given
        String workerOneId = IdUtils.create();
        String workerTwoId = IdUtils.create();

        Metric metricFromWorkerOne = buildMetric("metric-name", 10);
        Metric metricFromWorkerTwo = buildMetric("metric-name", 20);

        ServiceInstance workerOne = buildServiceInstanceWithMetric(workerOneId, metricFromWorkerOne);
        ServiceInstance workerTwo = buildServiceInstanceWithMetric(workerTwoId, metricFromWorkerTwo);

        mockServiceInstance(List.of(workerOne, workerTwo));

        // When
        sharedServiceInstanceMetricService.populateSharedServiceInstanceMetrics();

        // Then
        Optional<io.micrometer.core.instrument.Gauge> gauge = getGauge("metric-name");

        assertThat(gauge).isPresent();

        assertThat(gauge.get().value()).isEqualTo(30);
    }

    @Test
    void shouldNotRegisterGauge_givenMetricNotInSharedConfig_whenPolled() {
        // Given
        Metric metric = buildMetric("metric-name-not-in-shared-metric-config", 1);
        ServiceInstance serviceInstance = buildServiceInstanceWithMetric(metric);
        mockServiceInstance(List.of(serviceInstance));

        // When
        sharedServiceInstanceMetricService.populateSharedServiceInstanceMetrics();

        // Then
        Optional<io.micrometer.core.instrument.Gauge> gauge = getGaugeWithoutTag("metric-name-not-in-shared-metric-config", "tag-key");
        assertThat(gauge).isNotPresent();
    }

    @Test
    void shouldSetGaugeValueToZero_givenServiceInstanceDeactivated_whenPolledAgain() {
        // Given
        Metric metric = buildMetric("metric-name", 1);
        ServiceInstance activeServiceInstance = buildServiceInstanceWithMetric(metric);
        mockServiceInstance(List.of(activeServiceInstance));

        // When
        sharedServiceInstanceMetricService.populateSharedServiceInstanceMetrics();

        mockServiceInstance(List.of());
        sharedServiceInstanceMetricService.populateSharedServiceInstanceMetrics();
        Optional<io.micrometer.core.instrument.Gauge> gaugeAfterServiceInstanceDeactivate = getGauge("metric-name");

        // Then
        assertThat(gaugeAfterServiceInstanceDeactivate).isPresent();
        assertThat(gaugeAfterServiceInstanceDeactivate.get().value()).isEqualTo(0);
    }

    @Test
    void shouldRemoveMetricPrefixBeforeRegisteringMetric_givenMetricFromSharedServiceInstances() {
        // Given
        Metric metric = buildMetric("kestra.metric-name", 1);
        ServiceInstance serviceInstance = buildServiceInstanceWithMetric(metric);
        mockServiceInstance(List.of(serviceInstance));

        // When
        sharedServiceInstanceMetricService.populateSharedServiceInstanceMetrics();

        // Then
        Optional<io.micrometer.core.instrument.Gauge> gauge = getGauge("metric-name");
        assertThat(gauge).isPresent();
    }

    private Optional<io.micrometer.core.instrument.Gauge> getGaugeWithoutTag(String metricName, String tagKey) {
        return this.metricRegistry.find(metricName).gauges().stream()
            .filter(gauge -> gauge.getId().getTag(tagKey) == null)
            .findFirst();
    }

    private Optional<io.micrometer.core.instrument.Gauge> getGauge(String metricName) {
        return this.metricRegistry.find(metricName).gauges().stream().findFirst();
    }

    private Optional<io.micrometer.core.instrument.Gauge> getGaugeWithTags(String metricName, Map<String, String> tags) {
        return this.metricRegistry.find(metricName).gauges().stream()
            .filter(gauge ->
                tags.entrySet().stream().allMatch(entry -> Objects.equals(gauge.getId().getTag(entry.getKey()), entry.getValue()))
            ).findFirst();
    }

    private Optional<io.micrometer.core.instrument.Gauge> getGaugeWithTagKeys(String metricName, List<String> tagKeys) {
        return this.metricRegistry.find(metricName).gauges().stream()
            .filter(gauge -> {
                    List<String> gaugeTagKeys = gauge.getId().getTags().stream().map(Tag::getKey).toList();
                    return gaugeTagKeys.containsAll(tagKeys) && tagKeys.containsAll(gaugeTagKeys);
                }
            ).findFirst();
    }

    private void mockServiceInstance(List<ServiceInstance> serviceInstances) {
        Mockito.when(serviceInstanceRepositoryInterface.find(
            Mockito.eq(Pageable.unpaged()),
            Mockito.eq(Service.ServiceState.allRunningStates()),
            Mockito.eq(metricConfig.getSharedServiceInstanceMetrics().keySet())
        )).thenReturn(new ArrayListTotal<>(serviceInstances, serviceInstances.size()));
    }

    private ServiceInstance buildServiceInstanceWithMetric(Metric... metrics) {
        return buildServiceInstanceWithMetric(IdUtils.create(), metrics);
    }

    private ServiceInstance buildServiceInstanceWithMetric(String serviceInstanceId, Metric... metrics) {
        return new ServiceInstance(
            serviceInstanceId,
            ServiceType.WORKER,
            Service.ServiceState.RUNNING,
            null,
            null,
            null,
            null,
            null,
            null,
            Set.of(metrics),
            0
        );
    }

    private Metric buildMetric(String name, Number value, Metric.Tag... tags) {
        return new Metric(
            name,
            Gauge.TYPE,
            "description",
            null,
            List.of(tags),
            value
        );
    }
}