package io.kestra.webserver.controllers.api;

import io.kestra.core.junit.annotations.KestraTest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class InstanceControllerTest {
    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Test
    void getMaintenanceStatus() {
        var response = client.toBlocking().retrieve(
            "/api/v1/instance/maintenance/status",
            InstanceController.MaintenanceStatusResponse.class
        );

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("operational");
        assertThat(response.getMaintenanceDetails()).isNull();
    }
}
