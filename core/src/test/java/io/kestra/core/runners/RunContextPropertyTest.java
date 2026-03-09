package io.kestra.core.runners;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class RunContextPropertyTest {
    @Inject
    private TestRunContextFactory runContextFactory;

    @Test
    void asShouldReturnEmptyForNullProperty() throws IllegalVariableEvaluationException {
        var runContext = runContextFactory.of();

        var runContextProperty = new RunContextProperty<String>(null, runContext);
        assertThat(runContextProperty.as(String.class)).isEqualTo(Optional.empty());

        runContextProperty = new RunContextProperty<>(null, runContext);
        assertThat(runContextProperty.as(String.class, Map.of("key", "value"))).isEqualTo(Optional.empty());
    }

    @Test
    void asShouldRenderAProperty() throws IllegalVariableEvaluationException {
        var runContext = runContextFactory.of(Map.of("variable", "value"));

        var runContextProperty = new RunContextProperty<>(Property.<String>builder().expression("{{ variable }}").build(), runContext);
        assertThat(runContextProperty.as(String.class).orElseThrow()).isEqualTo("value");

        runContextProperty = new RunContextProperty<>(Property.<String>builder().expression("{{ key }}").build(), runContext);
        assertThat(runContextProperty.as(String.class, Map.of("key", "value")).orElseThrow()).isEqualTo("value");
    }

    @Test
    void asListShouldReturnEmptyForNullProperty() throws IllegalVariableEvaluationException {
        var runContext = runContextFactory.of();

        var runContextProperty = new RunContextProperty<List<String>>(null, runContext);
        assertThat(runContextProperty.asList(String.class)).hasSize(0);

        runContextProperty = new RunContextProperty<>(null, runContext);
        assertThat(runContextProperty.asList(String.class, Map.of("key", "value"))).hasSize(0);
    }

    @Test
    void asListShouldRenderAProperty() throws IllegalVariableEvaluationException {
        var runContext = runContextFactory.of(Map.of("variable", "value"));

        var runContextProperty = new RunContextProperty<>(Property.<List<String>>builder().expression("[\"{{ variable }}\"]").build(), runContext);
        assertThat(runContextProperty.asList(String.class)).contains("value");

        runContextProperty = new RunContextProperty<>(Property.<List<String>>builder().expression("[\"{{ key }}\"]").build(), runContext);
        assertThat(runContextProperty.asList(String.class, Map.of("key", "value"))).contains("value");
    }

    @Test
    void asMapShouldReturnEmptyForNullProperty() throws IllegalVariableEvaluationException {
        var runContext = runContextFactory.of();

        var runContextProperty = new RunContextProperty<Map<String, String>>(null, runContext);
        assertThat(runContextProperty.asMap(String.class, String.class)).hasSize(0);

        runContextProperty = new RunContextProperty<>(null, runContext);
        assertThat(runContextProperty.asMap(String.class, String.class, Map.of("key", "value"))).hasSize(0);
    }

    @Test
    void asMapShouldRenderAProperty() throws IllegalVariableEvaluationException {
        var runContext = runContextFactory.of(Map.of("variable", "value"));

        var runContextProperty = new RunContextProperty<>(Property.<Map<String, String>>builder().expression("{ \"key\": \"{{ variable }}\"}").build(), runContext);
        assertThat(runContextProperty.asMap(String.class, String.class)).containsEntry("key", "value");

        runContextProperty = new RunContextProperty<>(Property.<Map<String, String>>builder().expression("{ \"key\": \"{{ key }}\"}").build(), runContext);
        assertThat(runContextProperty.asMap(String.class, String.class, Map.of("key", "value"))).containsEntry("key", "value");
    }

    @Test
    void asShouldReturnCachedRenderedProperty() throws IllegalVariableEvaluationException {
        var runContext = runContextFactory.of();

        var runContextProperty = new RunContextProperty<>(Property.<String>builder().expression("{{ variable }}").build(), runContext);

        assertThat(runContextProperty.as(String.class, Map.of("variable", "value1"))).isEqualTo(Optional.of("value1"));
        assertThat(runContextProperty.as(String.class, Map.of("variable", "value2"))).isEqualTo(Optional.of("value1"));
    }

    @Test
    void asShouldNotReturnCachedRenderedPropertyWithSkipCache() throws IllegalVariableEvaluationException {
        var runContext = runContextFactory.of();

        var runContextProperty = new RunContextProperty<>(Property.<String>builder().expression("{{ variable }}").build(), runContext);

        assertThat(runContextProperty.as(String.class, Map.of("variable", "value1"))).isEqualTo(Optional.of("value1"));
        var skippedCache = runContextProperty.skipCache();
        assertThat(skippedCache.as(String.class, Map.of("variable", "value2"))).isEqualTo(Optional.of("value2"));
        // assure skipCache is preserved across calls
        assertThat(skippedCache.as(String.class, Map.of("variable", "value3"))).isEqualTo(Optional.of("value3"));
    }

    @Test
    void asShouldNotReturnCachedRenderedPropertyWithOfExpression() throws IllegalVariableEvaluationException {
        var runContext = runContextFactory.of();

        var runContextProperty = new RunContextProperty<String>(Property.ofExpression("{{ variable }}"), runContext);

        assertThat(runContextProperty.as(String.class, Map.of("variable", "value1"))).isEqualTo(Optional.of("value1"));
        assertThat(runContextProperty.as(String.class, Map.of("variable", "value2"))).isEqualTo(Optional.of("value2"));
    }
}