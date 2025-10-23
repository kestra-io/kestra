package io.kestra.plugin.core.templating;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.core.debug.Return;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class TemplatedTaskTest {

    @Inject
    private TestRunContextFactory runContextFactory;

    @Test
    void templatedType() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of("type", "io.kestra.plugin.core.debug.Return"));
        TemplatedTask templatedTask = TemplatedTask.builder()
            .id("template")
            .type(TemplatedTask.class.getName())
            .spec(Property.ofExpression("""
                type: {{ type }}
                format: It's alive!"""))
            .build();

        Output output = templatedTask.run(runContext);

        assertThat(output).isNotNull();
        assertThat(output).isInstanceOf(Return.Output.class);
        assertThat(((Return.Output) output).getValue()).isEqualTo("It's alive!");
    }

    @Test
    void templatedFlowable() {
        RunContext runContext = runContextFactory.of();
        TemplatedTask templatedTask = TemplatedTask.builder()
            .id("template")
            .type(TemplatedTask.class.getName())
            .spec(Property.ofValue("""
                type: io.kestra.plugin.core.flow.Pause
                delay: PT10S"""))
            .build();

        var exception = assertThrows(IllegalArgumentException.class, () -> templatedTask.run(runContext));
        assertThat(exception.getMessage()).isEqualTo("The templated task must be a runnable task");
    }

    @Test
    void templatedTemplated() {
        RunContext runContext = runContextFactory.of();
        TemplatedTask templatedTask = TemplatedTask.builder()
            .id("template")
            .type(TemplatedTask.class.getName())
            .spec(Property.ofValue("""
                type: io.kestra.plugin.core.templating.TemplatedTask
                spec: whatever"""))
            .build();

        var exception = assertThrows(IllegalArgumentException.class, () -> templatedTask.run(runContext));
        assertThat(exception.getMessage()).isEqualTo("The templated task cannot be of type 'io.kestra.plugin.core.templating.TemplatedTask'");
    }

}