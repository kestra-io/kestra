package io.kestra.webserver.controllers.api;

import io.kestra.webserver.controllers.api.MiscController;
import io.kestra.webserver.controllers.h2.JdbcH2ControllerTest;
import io.kestra.webserver.services.BasicAuthService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class MiscControllerTest extends JdbcH2ControllerTest {
    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    BasicAuthService basicAuthService;

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
        assertThat(response.getIsBasicAuthEnabled(), is(false));
    }

    @Test
    void basicAuth() {
        Assertions.assertDoesNotThrow(() -> client.toBlocking().retrieve("/api/v1/configs", MiscController.Configuration.class));

        String uid = "someUid";
        String username = "my.email@kestra.io";
        String password = "myPassword";
        client.toBlocking().exchange(HttpRequest.POST("/api/v1/basicAuth", new MiscController.BasicAuthCredentials(uid, username, password)));
        try {
            Assertions.assertThrows(
                HttpClientResponseException.class,
                () -> client.toBlocking().retrieve("/api/v1/configs", MiscController.Configuration.class)
            );
            Assertions.assertThrows(
                HttpClientResponseException.class,
                () -> client.toBlocking().retrieve(
                    HttpRequest.GET("/api/v1/configs")
                        .basicAuth("bad.user@kestra.io", "badPassword"),
                    MiscController.Configuration.class
                )
            );
            Assertions.assertDoesNotThrow(() -> client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/configs")
                    .basicAuth(username, password),
                MiscController.Configuration.class)
            );
        } finally {
            basicAuthService.unsecure();
        }
    }
}