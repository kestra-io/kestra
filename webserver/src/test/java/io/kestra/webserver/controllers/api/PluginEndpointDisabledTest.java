package io.kestra.webserver.controllers.api;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
@Property(name = "kestra.plugins.endpoint.enabled", value = "false")
class PluginEndpointDisabledTest {
    @Inject
    ApplicationContext applicationContext;

    @Test
    void shouldNotLoadControllerWhenDisabled() {
        assertThat(applicationContext.containsBean(PluginEndpointController.class)).isFalse();
    }
}
