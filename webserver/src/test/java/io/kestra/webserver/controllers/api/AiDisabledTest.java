package io.kestra.webserver.controllers.api;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static io.micronaut.http.HttpRequest.GET;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
@Property(name = "kestra.ai.enabled", value = "false")
class AiDisabledTest {
    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    ApplicationContext applicationContext;

    @Test
    void shouldDisableAiWhenPropertyIsFalse() {
        // Given - kestra.ai.enabled=false is set via @Property

        // When
        var response = client.toBlocking().retrieve(GET("/api/v1/configs"), MiscController.Configuration.class);

        // Then
        assertThat(response.getIsAiEnabled()).isFalse();
    }

    @Test
    void shouldNotRegisterAiController() {
        // Given - kestra.ai.enabled=false is set via @Property

        // Then
        assertThat(applicationContext.containsBean(AiController.class)).isFalse();
    }
}
