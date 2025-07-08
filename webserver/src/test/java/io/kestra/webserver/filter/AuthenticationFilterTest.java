package io.kestra.webserver.filter;

import io.kestra.webserver.services.BasicAuthService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class AuthenticationFilterTest {
    @Inject
    @Client("/")
    private ReactorHttpClient client;

    @Inject
    private BasicAuthService.BasicAuthConfiguration basicAuthConfiguration;

    @Inject
    private AuthenticationFilter filter;

    @Test
    void testUnauthorized() {
        assertThrows(HttpClientResponseException.class, () -> client.toBlocking()
            .exchange(HttpRequest.GET("/api/v1/configs").basicAuth("anonymous", "hacker")));
    }

    @Test
    void testAnonymous() {
        var response = client.toBlocking().exchange("/ping");

        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    }

    @Test
    void testManagementEndpoint() {
        var response = client.toBlocking().exchange("/health");

        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    }

    @Test
    void testAuthenticated() {
        var response = client.toBlocking()
            .exchange(HttpRequest.GET("/api/v1/configs").basicAuth(
                basicAuthConfiguration.getUsername(),
                basicAuthConfiguration.getPassword()
            ));

        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    }

    @Test
    void should_unauthorized_with_wrong_username() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking()
                .exchange(HttpRequest.GET("/api/v1/configs").basicAuth(
                    "incorrect",
                    basicAuthConfiguration.getPassword()
                )));

        assertThat(e.getResponse().getStatus().getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
    }

    @Test
    void should_unauthorized_with_wrong_password() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking()
                .exchange(HttpRequest.GET("/api/v1/configs").basicAuth(
                    basicAuthConfiguration.getUsername(),
                    "incorrect"
                )));

        assertThat(e.getResponse().getStatus().getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
    }

    @Test
    void should_unauthorized_without_token(){
        MutableHttpResponse<?> response = Mono.from(filter.doFilter(
            HttpRequest.GET("/api/v1/configs"), null)).block();
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
    }
}