package io.kestra.webserver.controllers.api;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.webserver.services.BasicAuthService;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
@Property(name = "kestra.system-flows.namespace", value = "some.system.ns")
class MiscControllerTest {
    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    BasicAuthService basicAuthService;

    @Test
    void ping() {
        var response = client.toBlocking().retrieve("/ping", String.class);

        assertThat(response).isEqualTo("pong");
    }

    @Test
    void configuration() {
        var response = client.toBlocking().retrieve("/api/v1/configs", MiscController.Configuration.class);

        assertThat(response).isNotNull();
        assertThat(response.getUuid()).isNotNull();
        assertThat(response.getIsTaskRunEnabled()).isEqualTo(false);
        assertThat(response.getIsAnonymousUsageEnabled()).isEqualTo(true);
        assertThat(response.getIsBasicAuthEnabled()).isEqualTo(false);
        assertThat(response.getSystemNamespace()).isEqualTo("some.system.ns");
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