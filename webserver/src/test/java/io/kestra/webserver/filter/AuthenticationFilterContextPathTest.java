package io.kestra.webserver.filter;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.webserver.services.BasicAuthService;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
@Property(name = "micronaut.server.context-path", value = "/kestra")
class AuthenticationFilterContextPathTest {
    @Inject
    @Client("/")
    private ReactorHttpClient client;

    @Inject
    private BasicAuthService.BasicAuthConfiguration basicAuthConfiguration;

    @Test
    void shouldRejectUnauthenticatedApiRequestWhenContextPathIsSet() {
        assertThat(statusCodeOf(HttpRequest.GET("/kestra/api/v1/main/flows/search"))).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
    }

    @Test
    void shouldRejectUnauthenticatedApiRequestWithEncodedSeparatorWhenContextPathIsSet() {
        assertThat(statusCodeOf(HttpRequest.GET("/kestra/api/v1%2Fmain/flows/search"))).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
    }

    @Test
    void shouldRejectUnauthenticatedApiRequestWithCollapsedSlashesWhenContextPathIsSet() {
        assertThat(statusCodeOf(HttpRequest.GET("/kestra//api/v1/main/flows/search"))).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
    }

    @Test
    void shouldRejectUnauthenticatedTenantlessApiRequestWhenContextPathIsSet() {
        assertThat(statusCodeOf(HttpRequest.GET("/kestra/api/v1/flows/search"))).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
    }

    @Test
    void shouldKeepLoginConfigPublicWhenContextPathIsSet() {
        var response = client.toBlocking().exchange(HttpRequest.GET("/kestra/api/v1/configs/login"));

        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    }

    @Test
    void shouldServeAuthenticatedApiRequestWhenContextPathIsSet() {
        var response = client.toBlocking().exchange(authenticated(HttpRequest.GET("/kestra/api/v1/main/flows/search")));

        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    }

    @Test
    void shouldAliasTenantlessApiRequestToMainTenantWhenContextPathIsSet() {
        var response = client.toBlocking().exchange(authenticated(HttpRequest.GET("/kestra/api/v1/flows/search")));

        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    }

    private int statusCodeOf(MutableHttpRequest<?> request) {
        // TestAuthFilter adds valid credentials to any request without an Authorization header
        request.header("Authorization", "");
        return assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(request)).getStatus().getCode();
    }

    private MutableHttpRequest<?> authenticated(MutableHttpRequest<?> request) {
        return request.basicAuth(basicAuthConfiguration.getUsername(), basicAuthConfiguration.getPassword());
    }
}
