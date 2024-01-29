package io.kestra.webserver.controllers;

import io.kestra.webserver.services.BasicAuthService;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest(transactional = false)
@Property(name = "kestra.server.basic-auth.enabled", value = "true")
class MiscControllerSecuredTest {
    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    private BasicAuthService.BasicAuthConfiguration basicAuthConfiguration;

    @Test
    void configuration() {
        var response = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/configs").basicAuth(
            basicAuthConfiguration.getUsername(),
            basicAuthConfiguration.getPassword()
        ), MiscController.Configuration.class);

        assertThat(response.getIsOauthEnabled(), is(true));
    }
}