package io.kestra.core.runners;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class RunContextPropertyTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void asShouldReturnEmptyForNullProperty() throws IllegalVariableEvaluationException {
        var runContext = runContextFactory.of();

        var runContextProperty = new RunContextProperty<String>(null, runContext);
        assertThat(runContextProperty.as(String.class), is(Optional.empty()));

        runContextProperty = new RunContextProperty<>(null, runContext);
        assertThat(runContextProperty.as(String.class, Map.of("key", "value")), is(Optional.empty()));
    }

    @Test
    void asShouldRenderAProperty() throws IllegalVariableEvaluationException {
        var runContext = runContextFactory.of(Map.of("variable", "value"));

        var runContextProperty = new RunContextProperty<>(Property.<String>builder().expression("{{ variable }}").build(), runContext);
        assertThat(runContextProperty.as(String.class).orElseThrow(), is("value"));

        runContextProperty = new RunContextProperty<>(Property.<String>builder().expression("{{ key }}").build(), runContext);
        assertThat(runContextProperty.as(String.class, Map.of("key", "value")).orElseThrow(), is("value"));
    }

    @Test
    void asListShouldReturnEmptyForNullProperty() throws IllegalVariableEvaluationException {
        var runContext = runContextFactory.of();

        var runContextProperty = new RunContextProperty<List<String>>(null, runContext);
        assertThat(runContextProperty.asList(String.class), hasSize(0));

        runContextProperty = new RunContextProperty<>(null, runContext);
        assertThat(runContextProperty.asList(String.class, Map.of("key", "value")), hasSize(0));
    }

    @Test
    void asListShouldRenderAProperty() throws IllegalVariableEvaluationException {
        var runContext = runContextFactory.of(Map.of("variable", "value"));

        var runContextProperty = new RunContextProperty<>(Property.<List<String>>builder().expression("[\"{{ variable }}\"]").build(), runContext);
        assertThat(runContextProperty.asList(String.class), hasItem("value"));

        runContextProperty = new RunContextProperty<>(Property.<List<String>>builder().expression("[\"{{ key }}\"]").build(), runContext);
        assertThat(runContextProperty.asList(String.class, Map.of("key", "value")), hasItem("value"));
    }

    @Test
    void asMapShouldReturnEmptyForNullProperty() throws IllegalVariableEvaluationException {
        var runContext = runContextFactory.of();

        var runContextProperty = new RunContextProperty<Map<String, String>>(null, runContext);
        assertThat(runContextProperty.asMap(String.class, String.class), aMapWithSize(0));

        runContextProperty = new RunContextProperty<>(null, runContext);
        assertThat(runContextProperty.asMap(String.class, String.class, Map.of("key", "value")), aMapWithSize(0));
    }

    @Test
    void asMapShouldRenderAProperty() throws IllegalVariableEvaluationException {
        var runContext = runContextFactory.of(Map.of("variable", "value"));

        var runContextProperty = new RunContextProperty<>(Property.<Map<String, String>>builder().expression("{ \"key\": \"{{ variable }}\"}").build(), runContext);
        assertThat(runContextProperty.asMap(String.class, String.class), hasEntry("key", "value"));

        runContextProperty = new RunContextProperty<>(Property.<Map<String, String>>builder().expression("{ \"key\": \"{{ key }}\"}").build(), runContext);
        assertThat(runContextProperty.asMap(String.class, String.class, Map.of("key", "value")), hasEntry("key", "value"));
    }
}