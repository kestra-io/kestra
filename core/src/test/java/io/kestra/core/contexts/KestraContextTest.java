package io.kestra.core.contexts;

import io.kestra.core.junit.annotations.KestraTest;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
class KestraContextTest {

    @Inject
    KestraContext context;

    @Test
    void shouldGetWorkerMaxNumThreads() {
        // When
        context.injectWorkerConfigs(16, null);

        // Then
        assertThat(KestraContext.getContext().getWorkerMaxNumThreads(), is(Optional.of(16)));
    }

    @Test
    void shouldGetWorkerGroupKey() {
        // When
        context.injectWorkerConfigs(null, "my-key");

        // Then
        assertThat(KestraContext.getContext().getWorkerGroupKey(), is(Optional.of("my-key")));
    }
}