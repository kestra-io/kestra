package io.kestra.webserver.services;

import io.kestra.core.metrics.MetricConfig;
import io.kestra.core.models.executions.metrics.Gauge;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.ServiceInstanceRepositoryInterface;
import io.kestra.core.server.Metric;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceType;
import io.kestra.core.utils.IdUtils;
import io.micronaut.data.model.Pageable;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;

class SharedServiceInstanceMetricServiceTest {
    private static final MetricConfig metricConfig = new MetricConfig(
        "kestra",
        Map.of(),
        List.of(),
        Map.of(
            ServiceType.WORKER,
            Set.of("metric-name")
        )
    );
    private ServiceInstanceRepositoryInterface serviceInstanceRepositoryInterface;

    @BeforeEach
    void setUp() {
        serviceInstanceRepositoryInterface = Mockito.mock(ServiceInstanceRepositoryInterface.class);
    }

    @Test
    void shouldPopulateMetricsFromSharedServiceInstances() {
        //given

        Metric metric = buildMetric("metric-name", 1);
        ServiceInstance serviceInstance = buildServiceInstanceWithMetric(metric);
        mockServiceInstance(List.of(serviceInstance));
        SharedServiceInstanceMetricService sharedServiceInstanceMetricService = new SharedServiceInstanceMetricService(
            ServiceType.WEBSERVER,
            metricConfig,
            serviceInstanceRepositoryInterface
        );

        // when
        sharedServiceInstanceMetricService.populateSharedServiceInstanceMetrics();


        // then
        assertThat(sharedServiceInstanceMetricService.getMetricNames()).contains("metric-name");
        assertThat(sharedServiceInstanceMetricService.getMetric("metric-name")).contains(
            buildMetric("metric-name", 1)
        );
    }

    @Test
    void shouldPopulateMetricForEachTagAndMetricNameCombinationFromSharedServiceInstances() {
        //given
        Metric metricWithOutTags = buildMetric("metric-name", 1);
        Metric metricWithTags = buildMetric("metric-name", 1, new Metric.Tag("tag-key", "tag-value"));

        ServiceInstance serviceInstance = buildServiceInstanceWithMetric(metricWithOutTags, metricWithTags);
        mockServiceInstance(List.of(serviceInstance));

        SharedServiceInstanceMetricService sharedServiceInstanceMetricService = new SharedServiceInstanceMetricService(
            ServiceType.WEBSERVER,
            metricConfig,
            serviceInstanceRepositoryInterface
        );

        // when
        sharedServiceInstanceMetricService.populateSharedServiceInstanceMetrics();


        // then
        assertThat(sharedServiceInstanceMetricService.getMetricNames()).contains(
            metricWithOutTags.name(),
            metricWithTags.name()
        );
        assertThat(sharedServiceInstanceMetricService.getMetric("metric-name")).contains(
            metricWithOutTags,
            metricWithTags
        );
    }


    @Test
    void shouldUpdateMetricFromSharedServiceInstances() {
        // given
        Metric initialMetric = buildMetric("metric-name", 1);
        ServiceInstance serviceInstance = buildServiceInstanceWithMetric(initialMetric);
        mockServiceInstance(List.of(serviceInstance));

        SharedServiceInstanceMetricService sharedServiceInstanceMetricService = new SharedServiceInstanceMetricService(
            ServiceType.WEBSERVER,
            metricConfig,
            serviceInstanceRepositoryInterface
        );
        sharedServiceInstanceMetricService.populateSharedServiceInstanceMetrics();

        assertThat(sharedServiceInstanceMetricService.getMetric("metric-name")).contains(initialMetric);

        // when
        Mockito.reset(serviceInstanceRepositoryInterface);
        Metric updatedMetric = buildMetric("metric-name", 2);
        ServiceInstance updatedServiceInstance = buildServiceInstanceWithMetric(updatedMetric);
        mockServiceInstance(List.of(updatedServiceInstance));

        sharedServiceInstanceMetricService.populateSharedServiceInstanceMetrics();

        // then
        assertThat(sharedServiceInstanceMetricService.getMetric("metric-name")).contains(updatedMetric);
        assertThat(sharedServiceInstanceMetricService.getMetric("metric-name")).doesNotContain(initialMetric);
    }

    private void mockServiceInstance(List<ServiceInstance> serviceInstances) {
        Mockito.when(serviceInstanceRepositoryInterface.find(
            Mockito.eq(Pageable.unpaged()),
            Mockito.eq(Service.ServiceState.allRunningStates()),
            Mockito.eq(metricConfig.getSharedServiceInstanceMetrics().keySet())
        )).thenReturn(new ArrayListTotal<>(serviceInstances, serviceInstances.size()));
    }

    private ServiceInstance buildServiceInstanceWithMetric(Metric... metrics) {
        return new ServiceInstance(
            IdUtils.create(),
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