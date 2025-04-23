package io.kestra.webserver.controllers.api;

import io.kestra.webserver.controllers.api.MiscController;
import io.kestra.webserver.services.BasicAuthService;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
@Property(name = "kestra.server.basic-auth.enabled", value = "true")
class MiscControllerSecuredTest {
    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    private BasicAuthService.BasicAuthConfiguration basicAuthConfiguration;

    @Test
    void getConfiguration() {
        var response = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/configs").basicAuth(
            basicAuthConfiguration.getUsername(),
            basicAuthConfiguration.getPassword()
        ), MiscController.Configuration.class);

        assertThat(response.getIsBasicAuthEnabled()).isTrue();
    }
}