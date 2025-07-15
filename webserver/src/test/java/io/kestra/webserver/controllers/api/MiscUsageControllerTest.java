package io.kestra.webserver.controllers.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.collectors.Usage;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@KestraTest
class MiscUsageControllerTest {

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Test
    void usages() {
        var response = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/usages/all"), Usage.class);

        assertThat(response.getUuid()).isNotNull();
        assertThat(response.getVersion()).isNotNull();
        assertThat(response.getStartTime()).isNotNull();
        assertThat(response.getEnvironments()).contains("test");
        assertThat(response.getStartTime()).isNotNull();
        assertThat(response.getHost().getUuid()).isNotNull();
        assertThat(response.getHost().getHardware().getLogicalProcessorCount()).isNotNull();
        assertThat(response.getHost().getJvm().getName()).isNotNull();
        assertThat(response.getHost().getOs().getFamily()).isNotNull();
        assertThat(response.getConfigurations().getRepositoryType()).isEqualTo("h2");
        assertThat(response.getConfigurations().getQueueType()).isEqualTo("h2");
    }
}