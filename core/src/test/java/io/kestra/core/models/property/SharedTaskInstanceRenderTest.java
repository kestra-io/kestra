package io.kestra.core.models.property;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.execution.Exit;
import io.kestra.plugin.core.execution.SetVariables;
import io.kestra.plugin.core.execution.UnsetVariables;
import io.kestra.plugin.core.flow.LoopUntil;
import io.kestra.plugin.core.flow.Parallel;
import io.kestra.plugin.core.flow.Pause;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The executor renders the tasks of the flow it keeps in its cache, so a single task instance is
 * shared by every execution of a flow revision. These tests render one instance twice, with the run
 * context of two different executions, and assert the second execution gets its own value.
 */
@MicronautTest
class SharedTaskInstanceRenderTest {

    @Inject
    private TestRunContextFactory runContextFactory;

    /** Mimics a property as deserialized from a flow: an expression, with no value rendered yet. */
    private static <T> Property<T> expression(String expression) {
        return Property.<T> builder().expression(expression).build();
    }

    private static Execution execution() {
        return Execution.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.unittest")
            .flowId("shared-task-instance")
            .flowRevision(1)
            .state(new State().withState(State.Type.RUNNING))
            .build();
    }

    @Test
    void shouldRenderSetVariablesForEachExecution() throws Exception {
        SetVariables task = SetVariables.builder()
            .id("set")
            .type(SetVariables.class.getName())
            .variables(expression("""
                {"isLts": "{{ inputs.version | startsWith('1.3.') }}"}"""))
            .build();

        Execution first = task.update(execution(), runContextFactory.of(Map.of("inputs", Map.of("version", "1.3.9"))));
        assertThat(first.getVariables()).containsEntry("isLts", "true");

        Execution second = task.update(execution(), runContextFactory.of(Map.of("inputs", Map.of("version", "1.0.56"))));
        assertThat(second.getVariables()).containsEntry("isLts", "false");
    }

    @Test
    void shouldRenderUnsetVariablesForEachExecution() throws Exception {
        UnsetVariables task = UnsetVariables.builder()
            .id("unset")
            .type(UnsetVariables.class.getName())
            .variables(expression("""
                ["{{ inputs.toUnset }}"]"""))
            .build();

        Execution first = task.update(
            execution().withVariables(variables()),
            runContextFactory.of(Map.of("inputs", Map.of("toUnset", "a")))
        );
        assertThat(first.getVariables()).containsOnlyKeys("b");

        Execution second = task.update(
            execution().withVariables(variables()),
            runContextFactory.of(Map.of("inputs", Map.of("toUnset", "b")))
        );
        assertThat(second.getVariables()).containsOnlyKeys("a");
    }

    @Test
    void shouldRenderExitStateForEachExecution() throws Exception {
        Exit task = Exit.builder()
            .id("exit")
            .type(Exit.class.getName())
            .state(expression("{{ inputs.exitState }}"))
            .build();

        Execution first = task.update(execution(), runContextFactory.of(Map.of("inputs", Map.of("exitState", "FAILED"))));
        assertThat(first.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        Execution second = task.update(execution(), runContextFactory.of(Map.of("inputs", Map.of("exitState", "WARNING"))));
        assertThat(second.getState().getCurrent()).isEqualTo(State.Type.WARNING);
    }

    @Test
    void shouldRenderParallelConcurrencyForEachExecution() throws Exception {
        Parallel task = Parallel.builder()
            .id("parallel")
            .type(Parallel.class.getName())
            .concurrent(expression("{{ inputs.limit }}"))
            .tasks(List.of(child("a"), child("b"), child("c"), child("d")))
            .build();

        Execution firstExecution = execution();
        List<NextTaskRun> first = task.resolveNexts(
            runContextFactory.of(Map.of("inputs", Map.of("limit", 1))),
            firstExecution,
            TaskRun.of(firstExecution, ResolvedTask.of(task))
        );
        assertThat(first).hasSize(1);

        Execution secondExecution = execution();
        List<NextTaskRun> second = task.resolveNexts(
            runContextFactory.of(Map.of("inputs", Map.of("limit", 4))),
            secondExecution,
            TaskRun.of(secondExecution, ResolvedTask.of(task))
        );
        assertThat(second).hasSize(4);
    }

    @Test
    void shouldRenderPauseDurationForEachExecution() throws Exception {
        // the executor renders pauseDuration itself, off the task instance of its cached flow
        Pause task = Pause.builder()
            .id("pause")
            .type(Pause.class.getName())
            .pauseDuration(expression("{{ inputs.pause }}"))
            .build();

        assertThat(renderDuration(task.getPauseDuration(), "pause", "PT10S")).isEqualTo(Duration.ofSeconds(10));
        assertThat(renderDuration(task.getPauseDuration(), "pause", "PT2.6S")).isEqualTo(Duration.ofMillis(2600));
    }

    @Test
    void shouldRenderLoopUntilCheckFrequencyForEachExecution() throws Exception {
        LoopUntil task = LoopUntil.builder()
            .id("loop")
            .type(LoopUntil.class.getName())
            .condition(expression("{{ false }}"))
            .checkFrequency(LoopUntil.CheckFrequency.builder().interval(expression("{{ inputs.interval }}")).build())
            .build();

        assertThat(renderDuration(task.getCheckFrequency().getInterval(), "interval", "PT1S")).isEqualTo(Duration.ofSeconds(1));
        assertThat(renderDuration(task.getCheckFrequency().getInterval(), "interval", "PT30S")).isEqualTo(Duration.ofSeconds(30));
    }

    private Duration renderDuration(Property<Duration> property, String key, String value) throws Exception {
        return runContextFactory.of(Map.of("inputs", Map.of(key, value)))
            .render(property)
            .as(Duration.class)
            .orElseThrow();
    }

    private static Map<String, Object> variables() {
        return new java.util.HashMap<>(Map.of("a", "1", "b", "2"));
    }

    private static Task child(String id) {
        return Return.builder().id(id).type(Return.class.getName()).format(Property.ofValue(id)).build();
    }
}
