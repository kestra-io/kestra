package io.kestra.webserver.controllers.api;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.server.ServerInstance;
import io.kestra.core.server.ServiceInstance;
import io.kestra.worker.DefaultWorker;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

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
}