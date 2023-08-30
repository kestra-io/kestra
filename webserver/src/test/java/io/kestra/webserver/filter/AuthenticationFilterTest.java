package io.kestra.webserver.filter;

import io.kestra.core.models.Setting;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.kestra.webserver.controllers.h2.JdbcH2ControllerTest;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.rxjava2.http.client.RxHttpClient;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolationException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Property(name = "kestra.server.basic-auth.enabled", value = "true")
class AuthenticationFilterTest extends JdbcH2ControllerTest {
    @Inject
    @Client("/")
    private RxHttpClient client;

    @Value("${kestra.server.basic-auth.username}")
    private String username;

    @Value("${kestra.server.basic-auth.password}")
    private String password;

    @Test
    void testUnauthorized() {
        assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange("/api/v1/configs"));

        assertThrows(HttpClientResponseException.class, () -> client.toBlocking()
            .exchange(HttpRequest.GET("/api/v1/configs").basicAuth("anonymous", "hacker")));
    }

    @Test
    void testAnonymous() {
        var response = client.toBlocking().exchange("/ping");

        assertThat(response.getStatus(), is(HttpStatus.OK));
    }

    @Test
    void testManagementEndpoint() {
        var response = client.toBlocking().exchange("/health");

        assertThat(response.getStatus(), is(HttpStatus.OK));
    }

    @Test
    void testAuthenticated() {
        var response = client.toBlocking()
            .exchange(HttpRequest.GET("/api/v1/configs").basicAuth(username, password));

        assertThat(response.getStatus(), is(HttpStatus.OK));
    }
}