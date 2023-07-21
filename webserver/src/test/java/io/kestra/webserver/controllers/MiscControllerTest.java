package io.kestra.webserver.controllers;

import io.kestra.core.models.collectors.ExecutionUsage;
import io.kestra.webserver.controllers.h2.JdbcH2ControllerTest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.rxjava2.http.client.RxHttpClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class MiscControllerTest extends JdbcH2ControllerTest {
    @Inject
    @Client("/")
    RxHttpClient client;

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
    }

    @Test
    void executionUsage() {
        var response = client.toBlocking().retrieve("/api/v1/execution-usage", ExecutionUsage.class);

        assertThat(response, notNullValue());
        // the memory executor didn't support daily statistics so we cannot have real execution usage
    }
}