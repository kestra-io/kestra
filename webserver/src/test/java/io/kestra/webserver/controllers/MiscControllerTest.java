package io.kestra.webserver.controllers;

import io.kestra.webserver.controllers.h2.JdbcH2ControllerTest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class MiscControllerTest extends JdbcH2ControllerTest {
    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Test
    void ping() {
        var response = client.toBlocking().retrieve("/ping", String.class);

        assertThat(response, is("pong"));
    }

    @Test
    void configuration() {
        var response = client.toBlocking().retrieve("/api/v1/configs", MiscController.Configuration.class);

        assertThat(response, notNullValue());
        assertThat(response.getUuid(), notNullValue());
        assertThat(response.getIsTaskRunEnabled(), is(false));
        assertThat(response.getIsAnonymousUsageEnabled(), is(true));
        assertThat(response.getIsOauthEnabled(), is(false));
    }
}