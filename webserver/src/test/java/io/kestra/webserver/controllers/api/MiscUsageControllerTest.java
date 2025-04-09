package io.kestra.webserver.controllers.api;

import io.kestra.core.Helpers;
import io.kestra.core.models.collectors.Usage;
import io.micronaut.http.HttpRequest;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MiscUsageControllerTest {
    @Test
    void usages() throws URISyntaxException {
        Helpers.runApplicationContext(new String[]{"test"}, Map.of("kestra.server-type", "STANDALONE"), (applicationContext, embeddedServer) -> {
            try (ReactorHttpClient client = ReactorHttpClient.create(embeddedServer.getURL())) {

                var response = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/usages/all"), Usage.class);

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
        });
    }
}