package io.kestra.webserver.controllers.api;

import io.kestra.core.Helpers;
import io.kestra.core.models.collectors.Usage;
import io.micronaut.http.HttpRequest;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class MiscUsageControllerTest {
    @Test
    void usages() throws URISyntaxException {
        Helpers.runApplicationContext(new String[]{"test"}, Map.of("kestra.server-type", "STANDALONE"), (applicationContext, embeddedServer) -> {
            try (ReactorHttpClient client = ReactorHttpClient.create(embeddedServer.getURL())) {

                var response = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/usages/all"), Usage.class);

                assertThat(response.getUuid(), notNullValue());
                assertThat(response.getVersion(), notNullValue());
                assertThat(response.getStartTime(), notNullValue());
                assertThat(response.getEnvironments(), contains("test"));
                assertThat(response.getStartTime(), notNullValue());
                assertThat(response.getHost().getUuid(), notNullValue());
                assertThat(response.getHost().getHardware().getLogicalProcessorCount(), notNullValue());
                assertThat(response.getHost().getJvm().getName(), notNullValue());
                assertThat(response.getHost().getOs().getFamily(), notNullValue());
                assertThat(response.getConfigurations().getRepositoryType(), is("h2"));
                assertThat(response.getConfigurations().getQueueType(), is("h2"));
            }
        });
    }
}