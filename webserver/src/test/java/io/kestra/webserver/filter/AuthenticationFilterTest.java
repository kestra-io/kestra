package io.kestra.webserver.filter;

import io.kestra.webserver.services.BasicAuthService;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.rxjava2.http.client.RxHttpClient;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest(transactional = false)
@Property(name = "kestra.server.basic-auth.enabled", value = "true")
class AuthenticationFilterTest {
    @Inject
    @Client("/")
    private RxHttpClient client;

    @Inject
    private BasicAuthService.BasicAuthConfiguration basicAuthConfiguration;

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
            .exchange(HttpRequest.GET("/api/v1/configs").basicAuth(
                basicAuthConfiguration.getUsername(),
                basicAuthConfiguration.getPassword()
            ));

        assertThat(response.getStatus(), is(HttpStatus.OK));
    }
}