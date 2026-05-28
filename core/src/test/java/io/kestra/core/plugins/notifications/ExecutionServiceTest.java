package io.kestra.core.plugins.notifications;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.serializers.JacksonMapper;
import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class ExecutionServiceTest {
    @Inject
    private TestRunContextFactory runContextFactory;

    @Test
    void shouldBuildExecutionMapForCurrentExecution() throws IllegalVariableEvaluationException {
        // Given
        Flow flow = TestsUtils.mockFlow();
        TaskRun successTask = mockTaskRun(flow, "success-task", State.Type.SUCCESS);
        Execution execution = Execution.builder()
            .id(IdUtils.create())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .state(new State())
            .taskRunList(List.of(successTask))
            .build()
            .withState(State.Type.SUCCESS);

        RunContext runContext = initRunContext(flow, execution);
        ExecutionInterface executionInterface = mockExecutionInterface(execution.getId());

        // When
        Map<String, Object> result = ExecutionService.executionMap(runContext, executionInterface);

        // Then
        assertThat(result).containsKeys("duration", "startDate", "execution", "firstFailed", "lastTask");
        assertThat(result.get("firstFailed")).isEqualTo(false);
        assertThat(result.get("lastTask")).isNotNull();
    }

    @Test
    void shouldBuildExecutionMapForCurrentExecutionWithFailedTask() throws IllegalVariableEvaluationException {
        // Given
        Flow flow = TestsUtils.mockFlow();
        TaskRun successTask = mockTaskRun(flow, "success-task", State.Type.SUCCESS);
        TaskRun failedTask = mockTaskRun(flow, "failed-task", State.Type.FAILED);
        Execution execution = Execution.builder()
            .id(IdUtils.create())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .state(new State())
            .taskRunList(List.of(successTask, failedTask))
            .build()
            .withState(State.Type.FAILED);

        RunContext runContext = initRunContext(flow, execution);
        ExecutionInterface executionInterface = mockExecutionInterface(execution.getId());

        // When
        Map<String, Object> result = ExecutionService.executionMap(runContext, executionInterface);

        // Then
        assertThat(result.get("firstFailed")).isNotNull().isNotEqualTo(false);
        assertThat(result.get("lastTask")).isNotNull();
    }

    @Test
    void shouldBuildExecutionMapForFlowTriggerExecution() throws IllegalVariableEvaluationException {
        // Given
        Flow flow = TestsUtils.mockFlow();
        String triggeredExecutionId = IdUtils.create();
        Instant startDate = Instant.now().minusSeconds(600);
        Instant endDate = Instant.now();

        io.kestra.plugin.core.trigger.Flow flowTrigger = io.kestra.plugin.core.trigger.Flow.builder()
            .id("flow-trigger")
            .type(io.kestra.plugin.core.trigger.Flow.class.getName())
            .build();

        io.kestra.plugin.core.trigger.Flow.Output triggerOutput = io.kestra.plugin.core.trigger.Flow.Output.builder()
            .executionId(triggeredExecutionId)
            .namespace("io.kestra.test")
            .flowId("triggered-flow")
            .flowRevision(1)
            .state(State.Type.SUCCESS)
            .startDate(startDate)
            .endDate(endDate)
            .executionLabels(Map.of())
            .build();

        Execution execution = Execution.builder()
            .id(IdUtils.create())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .state(new State())
            .trigger(ExecutionTrigger.of(flowTrigger, triggerOutput))
            .build()
            .withState(State.Type.SUCCESS);

        RunContext runContext = initRunContext(flow, execution);
        ExecutionInterface executionInterface = mockExecutionInterface(triggeredExecutionId);

        // When
        Map<String, Object> result = ExecutionService.executionMap(runContext, executionInterface);

        // Then
        assertThat(result).containsKeys("duration", "startDate", "execution", "firstFailed", "lastTask");
        assertThat(result.get("firstFailed")).isEqualTo(false);
    }

    @Test
    void shouldBuildExecutionMapForFlowTriggerExecutionWithFailedTask() throws IllegalVariableEvaluationException {
        // Given
        Flow flow = TestsUtils.mockFlow();
        String triggeredExecutionId = IdUtils.create();
        Instant startDate = Instant.now().minusSeconds(600);
        Instant endDate = Instant.now();

        io.kestra.plugin.core.trigger.Flow flowTrigger = io.kestra.plugin.core.trigger.Flow.builder()
            .id("flow-trigger")
            .type(io.kestra.plugin.core.trigger.Flow.class.getName())
            .build();

        io.kestra.plugin.core.trigger.Flow.Output triggerOutput = io.kestra.plugin.core.trigger.Flow.Output.builder()
            .executionId(triggeredExecutionId)
            .namespace("io.kestra.test")
            .flowId("triggered-flow")
            .flowRevision(1)
            .state(State.Type.FAILED)
            .startDate(startDate)
            .endDate(endDate)
            .firstFailedTaskId("first-failed-task")
            .lastTaskId("last-task")
            .executionLabels(Map.of())
            .build();

        Execution execution = Execution.builder()
            .id(IdUtils.create())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .state(new State())
            .trigger(ExecutionTrigger.of(flowTrigger, triggerOutput))
            .build()
            .withState(State.Type.SUCCESS);

        RunContext runContext = initRunContext(flow, execution);
        ExecutionInterface executionInterface = mockExecutionInterface(triggeredExecutionId);

        // When
        Map<String, Object> result = ExecutionService.executionMap(runContext, executionInterface);

        // Then
        assertThat(result.get("firstFailed")).isNotNull().isNotEqualTo(false);
        assertThat(result.get("lastTask")).isNotNull();
    }

    @Test
    void shouldIncludeCustomMessageWhenProvided() throws IllegalVariableEvaluationException {
        // Given
        Flow flow = TestsUtils.mockFlow();
        String triggeredExecutionId = IdUtils.create();
        String customMessage = "Custom notification message";

        Execution execution = buildExecutionWithFlowTrigger(flow, triggeredExecutionId);
        RunContext runContext = initRunContext(flow, execution);
        ExecutionInterface executionInterface = mockExecutionInterfaceWithCustomMessage(triggeredExecutionId, customMessage);

        // When
        Map<String, Object> result = ExecutionService.executionMap(runContext, executionInterface);

        // Then
        assertThat(result.get("customMessage")).isEqualTo(customMessage);
    }

    @Test
    void shouldNotIncludeCustomMessageWhenAbsent() throws IllegalVariableEvaluationException {
        // Given
        Flow flow = TestsUtils.mockFlow();
        String triggeredExecutionId = IdUtils.create();

        Execution execution = buildExecutionWithFlowTrigger(flow, triggeredExecutionId);
        RunContext runContext = initRunContext(flow, execution);
        ExecutionInterface executionInterface = mockExecutionInterface(triggeredExecutionId);

        // When
        Map<String, Object> result = ExecutionService.executionMap(runContext, executionInterface);

        // Then
        assertThat(result).doesNotContainKey("customMessage");
    }

    @Test
    void shouldIncludeCustomFieldsWhenProvided() throws IllegalVariableEvaluationException {
        // Given
        Flow flow = TestsUtils.mockFlow();
        String triggeredExecutionId = IdUtils.create();
        Map<String, Object> customFields = Map.of("env", "production", "team", "backend");

        Execution execution = buildExecutionWithFlowTrigger(flow, triggeredExecutionId);
        RunContext runContext = initRunContext(flow, execution);
        ExecutionInterface executionInterface = mockExecutionInterfaceWithCustomFields(triggeredExecutionId, customFields);

        // When
        Map<String, Object> result = ExecutionService.executionMap(runContext, executionInterface);

        // Then
        assertThat(result.get("customFields")).isEqualTo(customFields);
    }

    @Test
    void shouldNotIncludeCustomFieldsWhenEmpty() throws IllegalVariableEvaluationException {
        // Given
        Flow flow = TestsUtils.mockFlow();
        String triggeredExecutionId = IdUtils.create();

        Execution execution = buildExecutionWithFlowTrigger(flow, triggeredExecutionId);
        RunContext runContext = initRunContext(flow, execution);
        ExecutionInterface executionInterface = mockExecutionInterface(triggeredExecutionId);

        // When
        Map<String, Object> result = ExecutionService.executionMap(runContext, executionInterface);

        // Then
        assertThat(result).doesNotContainKey("customFields");
    }

    @Test
    void shouldThrowWhenTriggerVariableMissingExecutionId() {
        // Given - execution has no trigger, so trigger variable will be null
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, Map.of());
        RunContext runContext = initRunContext(flow, execution);

        // Use a different executionId to force the trigger execution path
        ExecutionInterface executionInterface = mockExecutionInterface("other-execution-id");

        // When / Then
        assertThatThrownBy(() -> ExecutionService.executionMap(runContext, executionInterface))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("executionId");
    }

    @Test
    void shouldThrowWhenNonFlowTriggerIsUsedWithDifferentExecutionId() {
        // Given - execution has a Schedule trigger (no executionId in variables)
        Flow flow = TestsUtils.mockFlow();
        io.kestra.plugin.core.trigger.Schedule scheduleTrigger = io.kestra.plugin.core.trigger.Schedule.builder()
            .id("schedule-trigger")
            .type(io.kestra.plugin.core.trigger.Schedule.class.getName())
            .cron("0 * * * *")
            .build();

        Execution execution = Execution.builder()
            .id(IdUtils.create())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .state(new State())
            .trigger(ExecutionTrigger.of(scheduleTrigger, Map.of("date", "2024-01-01")))
            .build()
            .withState(State.Type.SUCCESS);

        RunContext runContext = initRunContext(flow, execution);
        ExecutionInterface executionInterface = mockExecutionInterface("other-execution-id");

        // When / Then
        assertThatThrownBy(() -> ExecutionService.executionMap(runContext, executionInterface))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("executionId");
    }

    private RunContext initRunContext(Flow flow, Execution execution) {
        RunContext runContext = runContextFactory.of(flow, execution);

        // inside the Worker, the runContext variables would have been serialized/deserialized
        // to mimic that, we deserialize and re-serialize the runContext to create a new instance
        var serializedRunContext = JacksonMapper.toMap(runContext);
        var deserializedRunContext = JacksonMapper.toMap(serializedRunContext, DefaultRunContext.class);

        return runContextFactory.of(deserializedRunContext.getVariables());
    }

    private Execution buildExecutionWithFlowTrigger(Flow flow, String triggeredExecutionId) {
        io.kestra.plugin.core.trigger.Flow flowTrigger = io.kestra.plugin.core.trigger.Flow.builder()
            .id("flow-trigger")
            .type(io.kestra.plugin.core.trigger.Flow.class.getName())
            .build();

        io.kestra.plugin.core.trigger.Flow.Output triggerOutput = io.kestra.plugin.core.trigger.Flow.Output.builder()
            .executionId(triggeredExecutionId)
            .namespace("io.kestra.test")
            .flowId("triggered-flow")
            .flowRevision(1)
            .state(State.Type.SUCCESS)
            .startDate(Instant.now().minusSeconds(300))
            .endDate(Instant.now())
            .executionLabels(Map.of())
            .build();

        return Execution.builder()
            .id(IdUtils.create())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .state(new State())
            .trigger(ExecutionTrigger.of(flowTrigger, triggerOutput))
            .build()
            .withState(State.Type.SUCCESS);
    }

    private TaskRun mockTaskRun(Flow flow, String taskId, State.Type state) {
        return TaskRun.builder()
            .id(IdUtils.create())
            .taskId(taskId)
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .executionId("dummy")
            .state(new State().withState(state))
            .build();
    }

    private ExecutionInterface mockExecutionInterface(String executionId) {
        return new ExecutionInterface() {
            @Override
            public Property<String> getExecutionId() {
                return Property.ofValue(executionId);
            }

            @Override
            public Property<Map<String, Object>> getCustomFields() {
                return null;
            }

            @Override
            public Property<String> getCustomMessage() {
                return null;
            }
        };
    }

    private ExecutionInterface mockExecutionInterfaceWithCustomMessage(String executionId, String customMessage) {
        return new ExecutionInterface() {
            @Override
            public Property<String> getExecutionId() {
                return Property.ofValue(executionId);
            }

            @Override
            public Property<Map<String, Object>> getCustomFields() {
                return null;
            }

            @Override
            public Property<String> getCustomMessage() {
                return Property.ofValue(customMessage);
            }
        };
    }

    private ExecutionInterface mockExecutionInterfaceWithCustomFields(String executionId, Map<String, Object> customFields) {
        return new ExecutionInterface() {
            @Override
            public Property<String> getExecutionId() {
                return Property.ofValue(executionId);
            }

            @Override
            public Property<Map<String, Object>> getCustomFields() {
                return Property.ofValue(customFields);
            }

            @Override
            public Property<String> getCustomMessage() {
                return null;
            }
        };
    }
}
