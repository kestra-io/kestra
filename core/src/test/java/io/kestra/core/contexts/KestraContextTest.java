package io.kestra.core.contexts;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class KestraContextTest {

    @Inject
    KestraContext context;

    @Test
    void shouldGetWorkerMaxNumThreads() throws InterruptedException {
        // When
        context.injectWorkerConfigs(16, null);

        // Then
        assertThat(context.getWorkerMaxNumThreads()).isEqualTo(Optional.of(16));
    }

    @Test
    void shouldGetWorkerGroupKey() {
        // When
        context.injectWorkerConfigs(null, "my-key");

        // Then
        assertThat(context.getWorkerGroupKey()).isEqualTo(Optional.of("my-key"));
    }
}
