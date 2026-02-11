package io.kestra.core.runners;

import io.kestra.core.repositories.FlowRepositoryInterface;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    @Property(name = "kestra.server-type", value = "WORKER")
    void shouldThrowForAdditionalServicesInTheWorker() {
        var services = new Services(applicationContext);

        assertThat(services.observationRegistry()).isPresent();
        assertThat(services.variablesService()).isNotNull();
        assertThat(services.taskLogLineMatcher()).isNotNull();
        assertThat(services.tracerFactory()).isNotNull();
        assertThat(services.uriProvider()).isNotNull();
        var exception = assertThrows(RuntimeException.class, () -> services.additionalService(FlowRepositoryInterface.class));
        assertThat(exception.getMessage()).isEqualTo("Services.additionalService() cannot be used inside the Worker");
    }
}