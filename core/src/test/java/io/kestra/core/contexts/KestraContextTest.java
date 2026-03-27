package io.kestra.core.contexts;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class KestraContextTest {

    @Inject
    KestraContext context;

    @Test
    void shouldGetWorkerMaxNumThreads() {
        // When
        context.injectWorkerConfigs(16, null);

        // Then
        assertThat(KestraContext.getContext().getWorkerMaxNumThreads()).isEqualTo(Optional.of(16));
    }

    @Test
    void shouldGetWorkerGroupKey() {
        // When
        context.injectWorkerConfigs(null, "my-key");

        // Then
        assertThat(KestraContext.getContext().getWorkerGroupKey()).isEqualTo(Optional.of("my-key"));
    }
}