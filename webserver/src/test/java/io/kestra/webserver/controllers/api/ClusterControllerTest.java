package io.kestra.webserver.controllers.api;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.server.Metric;
import io.kestra.core.server.ServerInstance;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceType;
import io.kestra.worker.DefaultWorker;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
class ClusterControllerTest {
    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    DefaultWorker worker;

    @Test
    void shouldGetServiceInfo() {
        ServiceInstance serviceInstance = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/cluster/services/" + worker.getId()),
            ServiceInstance.class
        );

        assertThat(serviceInstance).isNotNull();
        assertThat(serviceInstance.server().type()).isEqualTo(ServerInstance.Type.STANDALONE);
    }


    @Test
    void shouldGetWorkerMetrics() {
        // When
        Set<Metric> metrics = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/cluster/metrics/" + ServiceType.WORKER),
            Argument.of(Set.class, Metric.class)
        );

        // Then
        assertThat(metrics).isNotEmpty();
    }
}