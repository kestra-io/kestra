package io.kestra.core.runners;

import org.junit.jupiter.api.Test;

import io.kestra.core.repositories.FlowRepositoryInterface;

import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest(rebuildContext = true)
class ServicesTest {
    @Inject
    private ApplicationContext applicationContext;

    @Test
    void shouldReturnBeans() {
        var services = new Services(applicationContext);

        assertThat(services.observationRegistry()).isPresent();
        assertThat(services.variablesService()).isNotNull();
        assertThat(services.taskLogLineMatcher()).isNotNull();
        assertThat(services.tracerFactory()).isNotNull();
        assertThat(services.uriProvider()).isNotNull();
        assertThat(services.additionalService(FlowRepositoryInterface.class)).isNotNull();
    }
}