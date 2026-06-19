package io.kestra.core.runners.pebble.functions;

import org.junit.jupiter.api.Test;

import io.kestra.core.runners.pebble.Extension;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Verifies the {@code subflow()} function is only wired in on server types that render execute forms.
 * The core test environment leaves {@code kestra.server-type} unset, so each test sets it explicitly:
 * under {@code STANDALONE} the bean is present, while under {@code WORKER} the {@code @Requires}
 * condition excludes it and {@link Extension#getFunctions()} must skip it without throwing.
 */
@MicronautTest(rebuildContext = true)
class SubflowFunctionServerTypeTest {
    @Inject
    private ApplicationContext applicationContext;

    @Test
    @Property(name = "kestra.server-type", value = "STANDALONE")
    void shouldRegisterSubflowFunctionOnStandalone() {
        // Given a STANDALONE server-type
        // When / Then the bean is present and registered as a Pebble function
        assertThat(applicationContext.containsBean(SubflowFunction.class)).isTrue();
        assertThat(applicationContext.getBean(Extension.class).getFunctions()).containsKey(SubflowFunction.NAME);
    }

    @Test
    @Property(name = "kestra.server-type", value = "WORKER")
    void shouldNotRegisterSubflowFunctionOnWorker() {
        // Given a WORKER server-type
        // When / Then the bean is absent and Extension still builds its functions without it
        assertThat(applicationContext.containsBean(SubflowFunction.class)).isFalse();
        assertThatCode(() -> assertThat(applicationContext.getBean(Extension.class).getFunctions()).doesNotContainKey(SubflowFunction.NAME))
            .doesNotThrowAnyException();
    }
}
