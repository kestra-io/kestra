package io.kestra.webserver.controllers.api;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static io.micronaut.http.HttpRequest.GET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
@Property(name = "kestra.webserver.ui.enabled", value = "false")
class UiDisabledWebserverTest {
    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    ApplicationContext applicationContext;

    @Test
    void shouldNotServeUiWhenUiIsDisabled() {
        // When / Then
        assertThatThrownBy(() -> client.toBlocking().exchange(GET("/ui/")))
            .isInstanceOf(HttpClientResponseException.class)
            .extracting(e -> ((HttpClientResponseException) e).getStatus())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldNotRedirectRootToUiWhenUiIsDisabled() {
        // When / Then
        assertThatThrownBy(() -> client.toBlocking().exchange(GET("/")))
            .isInstanceOf(HttpClientResponseException.class)
            .extracting(e -> ((HttpClientResponseException) e).getStatus())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldKeepApiAvailableWhenUiIsDisabled() {
        // When
        String response = client.toBlocking().retrieve(GET("/ping"));

        // Then
        assertThat(response).isEqualTo("pong");
    }

    @Test
    void shouldNotRegisterUiBeansWhenUiIsDisabled() {
        // Then
        assertThat(applicationContext.containsBean(RedirectController.class)).isFalse();
        assertThat(applicationContext.containsBean(StaticFilter.class)).isFalse();
    }
}
