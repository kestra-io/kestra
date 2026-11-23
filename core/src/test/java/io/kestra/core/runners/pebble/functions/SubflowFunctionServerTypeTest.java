package io.kestra.core.runners.pebble.functions;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.kestra.core.runners.pebble.Extension;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the {@code subflow()} function is only wired in on server types that render execute forms.
 * The test environment defaults to {@code STANDALONE} (see {@code application-test.yml}), so the bean
 * is present by default; under {@code WORKER} the {@code @Requires} condition excludes it and
 * {@link Extension#getFunctions()} must register {@link SubflowFunction#unavailable()} instead.
 */
@MicronautTest(rebuildContext = true)
class SubflowFunctionServerTypeTest {
    @Inject
    private ApplicationContext applicationContext;

    @Test
    void shouldRegisterSubflowFunctionOnStandalone() {
        // Given the default test server-type (STANDALONE)
        // When / Then the bean is present and registered as a Pebble function
        assertThat(applicationContext.containsBean(SubflowFunction.class)).isTrue();
        assertThat(applicationContext.getBean(Extension.class).getFunctions()).containsKey(SubflowFunction.NAME);
    }

    @Test
    @Property(name = "kestra.server-type", value = "WORKER")
    void shouldRegisterUnavailableSubflowFunctionOnWorker() {
        // Given a WORKER server-type
        // When / Then the bean is absent and the function fails with an actionable message instead of being unknown
        assertThat(applicationContext.containsBean(SubflowFunction.class)).isFalse();
        Function function = applicationContext.getBean(Extension.class).getFunctions().get(SubflowFunction.NAME);
        assertThat(function).isNotNull();
        PebbleTemplate template = Mockito.mock(PebbleTemplate.class);
        assertThatThrownBy(() -> function.execute(Map.of(), template, null, 1))
            .isInstanceOf(PebbleException.class)
            .hasMessageContaining("pass the value of this input from the caller");
    }
}
