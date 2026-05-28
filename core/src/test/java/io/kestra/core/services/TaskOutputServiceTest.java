package io.kestra.core.services;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.kestra.core.repositories.ExecutionRepositoryInterface;
import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunWithOutput;
import io.kestra.core.models.executions.Variables;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.repositories.TaskOutputRepositoryInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;
import lombok.Builder;
import lombok.Getter;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
class TaskOutputServiceTest {
    @Inject
    private TaskOutputService taskOutputService;
    @Inject
    private TaskOutputRepositoryInterface taskOutputRepository;
    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Test
    @ExecuteFlow("flows/valids/loop-switch.yaml")
    void outputsEachSwitch(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(3);

        var subExecutions = executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId());
        assertThat(subExecutions.size()).isEqualTo(2);

        Map<String, Object> outputs = taskOutputService.computeOutputs(subExecutions.getFirst());
        assertThat(outputs).hasSize(4);
        @SuppressWarnings("unchecked")
        Map<String, Object> switchLetterAOut = (Map<String, Object>) outputs.get("2-1_switch-letter-a");
        assertThat(switchLetterAOut).containsExactlyInAnyOrderEntriesOf(Map.of("value", "2-1_switch-letter-a"));
        @SuppressWarnings("unchecked")
        Map<String, Object> switchOut = (Map<String, Object>) outputs.get("2-1_switch");
        assertThat(switchOut).containsExactlyInAnyOrderEntriesOf(Map.of("defaults", false, "value", "a"));
        @SuppressWarnings("unchecked")
        Map<String, Object> eachOut = (Map<String, Object>) outputs.get("2_each");
        assertThat(eachOut).containsExactlyInAnyOrderEntriesOf(Map.of("terminatedIterations", 2, "runningIterations", 0, "iterationCount", 2));
        @SuppressWarnings("unchecked")
        Map<String, Object> t1Out = (Map<String, Object>) outputs.get("t1");
        assertThat(t1Out).containsExactlyInAnyOrderEntriesOf(Map.of("value", "t1"));
    }

    @Test
    @ExecuteFlow("flows/valids/loop-object-in-list.yaml")
    void outputsEachObjectInList(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(2);

        var subExecutions = executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId());
        assertThat(subExecutions.size()).isEqualTo(3);

        Map<String, Object> outputs = taskOutputService.computeOutputs(subExecutions.getFirst());
        assertThat(outputs).hasSize(3);
        @SuppressWarnings("unchecked")
        Map<String, Object> notJsonOut = (Map<String, Object>) outputs.get("not-json");
        assertThat(notJsonOut).containsExactlyInAnyOrderEntriesOf(Map.of("value", "not-json > STRING > value 1"));
        @SuppressWarnings("unchecked")
        Map<String, Object> isJsonOut = (Map<String, Object>) outputs.get("is-json");
        assertThat(isJsonOut).containsExactlyInAnyOrderEntriesOf(Map.of("defaults", false, "value", "false"));
        @SuppressWarnings("unchecked")
        Map<String, Object> eachOut = (Map<String, Object>) outputs.get("1_each");
        assertThat(eachOut).containsExactlyInAnyOrderEntriesOf(Map.of("terminatedIterations", 3, "runningIterations", 0, "iterationCount", 3));
    }

    @Test
    @ExecuteFlow("flows/valids/if-in-flowable.yaml")
    void outputsIfInFlowable(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(1);

        var subExecutions = executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId());
        assertThat(subExecutions.size()).isEqualTo(3);

        Map<String, Object> outputs = taskOutputService.computeOutputs(subExecutions.getFirst());
        assertThat(outputs).hasSize(3);
        @SuppressWarnings("unchecked")
        Map<String, Object> beforeIfOut = (Map<String, Object>) outputs.get("before_if");
        assertThat(beforeIfOut).containsExactlyInAnyOrderEntriesOf(Map.of("value", "Before if: value 1"));
        @SuppressWarnings("unchecked")
        Map<String, Object> ifOut = (Map<String, Object>) outputs.get("if");
        assertThat(ifOut).containsExactlyInAnyOrderEntriesOf(Map.of("evaluationResult", false));
        @SuppressWarnings("unchecked")
        Map<String, Object> forEachOut = (Map<String, Object>) outputs.get("for_each");
        assertThat(forEachOut).containsExactlyInAnyOrderEntriesOf(Map.of("terminatedIterations", 3, "runningIterations", 0, "iterationCount", 3));
    }

    @Test
    @ExecuteFlow("flows/valids/waitfor-multiple-tasks.yaml")
    void outputsWaitForMultipleTasks(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(3);

        Map<String, Object> outputs = taskOutputService.computeOutputs(execution);
        assertThat(outputs).hasSize(3);
        @SuppressWarnings("unchecked")
        Map<String, Object> outputValues = (Map<String, Object>) outputs.get("output_values");
        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) outputValues.get("values");
        assertThat(values).containsExactlyInAnyOrderEntriesOf(Map.of("count", "4"));
        @SuppressWarnings("unchecked")
        Map<String, Object> echo = (Map<String, Object>) outputs.get("echo");
        assertThat(echo).isEmpty();
        @SuppressWarnings("unchecked")
        Map<String, Object> waitfor = (Map<String, Object>) outputs.get("waitfor");
        assertThat(waitfor).containsExactlyInAnyOrderEntriesOf(Map.of("iterationCount", 3));
    }

    @Test
    void saveOutputs_withTaskRunWithOutput() throws InternalException {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = IdUtils.create();
        String taskRunId = IdUtils.create();

        TaskRun taskRun = TaskRun.builder()
            .id(taskRunId)
            .tenantId(tenant)
            .executionId(executionId)
            .namespace("io.kestra.test")
            .flowId("test-flow")
            .taskId("test-task")
            .state(new State())
            .build();

        Map<String, Object> outputMap = Map.of(
            "key1", "value1",
            "key2", 42,
            "key3", Map.of("nested", "value")
        );

        TaskRunWithOutput taskRunWithOutput = new TaskRunWithOutput(taskRun, outputMap);

        // When
        taskOutputService.saveOutputs(taskRunWithOutput);

        // Then
        Map<String, Object> retrievedOutputs = taskOutputService.getOutputs(taskRun);
        assertThat(retrievedOutputs).isNotNull();
        assertThat(retrievedOutputs).hasSize(3);
        assertThat(retrievedOutputs.get("key1")).isEqualTo("value1");
        assertThat(retrievedOutputs.get("key2")).isEqualTo(42);
        assertThat(retrievedOutputs.get("key3")).isInstanceOf(Map.class);

        // Verify it was saved in repository
        var savedOutput = taskOutputRepository.findById(tenant, taskRunId);
        assertThat(savedOutput).isPresent();
        assertThat(savedOutput.get().taskRunId()).isEqualTo(taskRunId);
        assertThat(savedOutput.get().executionId()).isEqualTo(executionId);
    }

    @Test
    void saveOutputs_withTaskRunAndOutputInterface() throws InternalException {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = IdUtils.create();
        String taskRunId = IdUtils.create();

        TaskRun taskRun = TaskRun.builder()
            .id(taskRunId)
            .tenantId(tenant)
            .executionId(executionId)
            .namespace("io.kestra.test")
            .flowId("test-flow")
            .taskId("test-task")
            .state(new State())
            .build();

        TestOutput output = TestOutput.builder()
            .message("Test message")
            .count(100)
            .success(true)
            .build();

        // When
        taskOutputService.saveOutputs(taskRun, output);

        // Then
        Map<String, Object> retrievedOutputs = taskOutputService.getOutputs(taskRun);
        assertThat(retrievedOutputs).isNotNull();
        assertThat(retrievedOutputs).containsEntry("message", "Test message");
        assertThat(retrievedOutputs).containsEntry("count", 100);
        assertThat(retrievedOutputs).containsEntry("success", true);

        // Verify it was saved in repository
        var savedOutput = taskOutputRepository.findById(tenant, taskRunId);
        assertThat(savedOutput).isPresent();
    }

    @Test
    void saveOutputs_withTaskRunAndMap() throws InternalException {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = IdUtils.create();
        String taskRunId = IdUtils.create();

        TaskRun taskRun = TaskRun.builder()
            .id(taskRunId)
            .tenantId(tenant)
            .executionId(executionId)
            .namespace("io.kestra.test")
            .flowId("test-flow")
            .taskId("test-task")
            .state(new State())
            .build();

        Map<String, Object> outputMap = Map.of(
            "status", "completed",
            "duration", 1234,
            "items", java.util.List.of("item1", "item2", "item3")
        );

        // When
        taskOutputService.saveOutputs(taskRun, outputMap);

        // Then
        Map<String, Object> retrievedOutputs = taskOutputService.getOutputs(taskRun);
        assertThat(retrievedOutputs).isNotNull();
        assertThat(retrievedOutputs).hasSize(3);
        assertThat(retrievedOutputs.get("status")).isEqualTo("completed");
        assertThat(retrievedOutputs.get("duration")).isEqualTo(1234);
        assertThat(retrievedOutputs.get("items")).isInstanceOf(java.util.List.class);
        assertThat((java.util.List<?>) retrievedOutputs.get("items")).hasSize(3);

        // Verify it was saved in repository
        var savedOutput = taskOutputRepository.findById(tenant, taskRunId);
        assertThat(savedOutput).isPresent();
        assertThat(savedOutput.get().value()).isNotNull();
    }

    @Test
    void saveOutputs_withEmptyMap_shouldNotSave() throws InternalException {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = IdUtils.create();
        String taskRunId = IdUtils.create();

        TaskRun taskRun = TaskRun.builder()
            .id(taskRunId)
            .tenantId(tenant)
            .executionId(executionId)
            .namespace("io.kestra.test")
            .flowId("test-flow")
            .taskId("test-task")
            .state(new State())
            .build();

        // When
        taskOutputService.saveOutputs(taskRun, Collections.emptyMap());

        // Then - should not save anything
        var savedOutput = taskOutputRepository.findById(tenant, taskRunId);
        assertThat(savedOutput).isEmpty();

        Map<String, Object> retrievedOutputs = taskOutputService.getOutputs(taskRun);
        assertThat(retrievedOutputs).isEmpty();
    }

    @Test
    void saveOutputs_withNullOutput_shouldNotSave() throws InternalException {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = IdUtils.create();
        String taskRunId = IdUtils.create();

        TaskRun taskRun = TaskRun.builder()
            .id(taskRunId)
            .tenantId(tenant)
            .executionId(executionId)
            .namespace("io.kestra.test")
            .flowId("test-flow")
            .taskId("test-task")
            .state(new State())
            .build();

        // When
        taskOutputService.saveOutputs(taskRun, (Output) null);

        // Then - should not save anything
        var savedOutput = taskOutputRepository.findById(tenant, taskRunId);
        assertThat(savedOutput).isEmpty();

        Map<String, Object> retrievedOutputs = taskOutputService.getOutputs(taskRun);
        assertThat(retrievedOutputs).isEmpty();
    }

    @Test
    void getOutputs_shouldReturnEmptyMapWhenNotFound() throws InternalException {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = IdUtils.create();
        String taskRunId = IdUtils.create();

        TaskRun taskRun = TaskRun.builder()
            .id(taskRunId)
            .tenantId(tenant)
            .executionId(executionId)
            .namespace("io.kestra.test")
            .flowId("test-flow")
            .taskId("test-task")
            .state(new State())
            .build();

        // When
        Map<String, Object> retrievedOutputs = taskOutputService.getOutputs(taskRun);

        // Then
        assertThat(retrievedOutputs).isEmpty();
    }

    @Test
    void saveAndGetOutputs_withComplexNestedStructure() throws InternalException {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = IdUtils.create();
        String taskRunId = IdUtils.create();

        TaskRun taskRun = TaskRun.builder()
            .id(taskRunId)
            .tenantId(tenant)
            .executionId(executionId)
            .namespace("io.kestra.test")
            .flowId("test-flow")
            .taskId("test-task")
            .state(new State())
            .build();

        Map<String, Object> complexOutput = Map.of(
            "level1", Map.of(
                "level2", Map.of(
                    "level3", "deep value",
                    "number", 999
                ),
                "list", java.util.List.of(1, 2, 3, 4, 5)
            ),
            "simple", "value",
            "mixed", java.util.List.of(
                Map.of("id", 1, "name", "first"),
                Map.of("id", 2, "name", "second")
            )
        );

        // When
        taskOutputService.saveOutputs(taskRun, complexOutput);
        Map<String, Object> retrievedOutputs = taskOutputService.getOutputs(taskRun);

        // Then
        assertThat(retrievedOutputs).isNotNull();
        assertThat(retrievedOutputs.get("simple")).isEqualTo("value");
        assertThat(retrievedOutputs.get("level1")).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> level1 = (Map<String, Object>) retrievedOutputs.get("level1");
        assertThat(level1.get("level2")).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> level2 = (Map<String, Object>) level1.get("level2");
        assertThat(level2.get("level3")).isEqualTo("deep value");
        assertThat(level2.get("number")).isEqualTo(999);
    }

    @Test
    void purge() throws InternalException {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        // Execution 1
        String executionId1 = IdUtils.create();
        String taskRunId1 = IdUtils.create();
        TaskRun taskRun1 = TaskRun.builder()
            .id(taskRunId1)
            .tenantId(tenant)
            .executionId(executionId1)
            .namespace("io.kestra.test")
            .flowId("test-flow")
            .taskId("test-task")
            .state(new State())
            .build();
        taskOutputService.saveOutputs(taskRun1, Map.of("key", "value1"));

        // Execution 2
        String executionId2 = IdUtils.create();
        String taskRunId2 = IdUtils.create();
        TaskRun taskRun2 = TaskRun.builder()
            .id(taskRunId2)
            .tenantId(tenant)
            .executionId(executionId2)
            .namespace("io.kestra.test")
            .flowId("test-flow")
            .taskId("test-task")
            .state(new State())
            .build();
        taskOutputService.saveOutputs(taskRun2, Map.of("key", "value2"));

        // Purge Execution 1
        Execution execution1 = Execution.builder().id(executionId1).tenantId(tenant).build();
        int purgedCount = taskOutputService.purge(java.util.List.of(execution1));

        // Verify
        assertThat(purgedCount).isEqualTo(1);
        assertThat(taskOutputRepository.findById(tenant, taskRunId1)).isEmpty();
        assertThat(taskOutputRepository.findById(tenant, taskRunId2)).isPresent();
    }

    @Test
    @SuppressWarnings("deprecation")
    void getOutputs_shouldReturnDeprecatedOutputsFieldWhenSet() throws InternalException {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String taskRunId = IdUtils.create();

        Map<String, Object> legacyOutputs = Map.of("legacyKey", "legacyValue", "count", 42);
        TaskRun taskRun = TaskRun.builder()
            .id(taskRunId)
            .tenantId(tenant)
            .executionId(IdUtils.create())
            .namespace("io.kestra.test")
            .flowId("test-flow")
            .taskId("test-task")
            .state(new State())
            .outputs(Variables.inMemory(legacyOutputs))
            .build();

        // When - no output saved to repository; should use deprecated field directly
        Map<String, Object> retrievedOutputs = taskOutputService.getOutputs(taskRun);

        // Then - exact match to confirm no extra data was merged from the repository
        assertThat(retrievedOutputs).containsExactlyInAnyOrderEntriesOf(legacyOutputs);
    }

    @Test
    @SuppressWarnings("deprecation")
    void computeOutputs_shouldReturnDeprecatedOutputsFieldWhenSet() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = IdUtils.create();

        TaskRun taskRun1 = TaskRun.builder()
            .id(IdUtils.create())
            .tenantId(tenant)
            .executionId(executionId)
            .namespace("io.kestra.test")
            .flowId("test-flow")
            .taskId("task-1")
            .state(new State())
            .outputs(Variables.inMemory(Map.of("output1", "value1")))
            .build();

        TaskRun taskRun2 = TaskRun.builder()
            .id(IdUtils.create())
            .tenantId(tenant)
            .executionId(executionId)
            .namespace("io.kestra.test")
            .flowId("test-flow")
            .taskId("task-2")
            .state(new State())
            .outputs(Variables.inMemory(Map.of("output2", "value2")))
            .build();

        Execution execution = Execution.builder()
            .id(executionId)
            .tenantId(tenant)
            .namespace("io.kestra.test")
            .flowId("test-flow")
            .flowRevision(1)
            .state(new State())
            .taskRunList(List.of(taskRun1, taskRun2))
            .build();

        // When - no outputs saved to repository; should use deprecated field directly
        Map<String, Object> outputs = taskOutputService.computeOutputs(execution);

        // Then
        assertThat(outputs).hasSize(2);

        @SuppressWarnings("unchecked")
        Map<String, Object> task1Outputs = (Map<String, Object>) outputs.get("task-1");
        assertThat(task1Outputs).containsExactlyInAnyOrderEntriesOf(Map.of("output1", "value1"));

        @SuppressWarnings("unchecked")
        Map<String, Object> task2Outputs = (Map<String, Object>) outputs.get("task-2");
        assertThat(task2Outputs).containsExactlyInAnyOrderEntriesOf(Map.of("output2", "value2"));
    }

    @Test
    @SuppressWarnings("deprecation")
    void computeOutputs_shouldHandleMixedLegacyAndModernTaskRuns() throws InternalException {
        // Given: one V1 task run (deprecated outputs field) and one V2 task run (outputs in repository)
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = IdUtils.create();

        TaskRun legacyTaskRun = TaskRun.builder()
            .id(IdUtils.create())
            .tenantId(tenant)
            .executionId(executionId)
            .namespace("io.kestra.test")
            .flowId("test-flow")
            .taskId("task-legacy")
            .state(new State())
            .outputs(Variables.inMemory(Map.of("source", "legacy")))
            .build();

        TaskRun modernTaskRun = TaskRun.builder()
            .id(IdUtils.create())
            .tenantId(tenant)
            .executionId(executionId)
            .namespace("io.kestra.test")
            .flowId("test-flow")
            .taskId("task-modern")
            .state(new State())
            .build();

        taskOutputService.saveOutputs(modernTaskRun, Map.of("source", "modern"));

        Execution execution = Execution.builder()
            .id(executionId)
            .tenantId(tenant)
            .namespace("io.kestra.test")
            .flowId("test-flow")
            .flowRevision(1)
            .state(new State())
            .taskRunList(List.of(legacyTaskRun, modernTaskRun))
            .build();

        // When
        Map<String, Object> outputs = taskOutputService.computeOutputs(execution);

        // Then
        assertThat(outputs).hasSize(2);

        @SuppressWarnings("unchecked")
        Map<String, Object> legacyOutputs = (Map<String, Object>) outputs.get("task-legacy");
        assertThat(legacyOutputs).containsEntry("source", "legacy");

        @SuppressWarnings("unchecked")
        Map<String, Object> modernOutputs = (Map<String, Object>) outputs.get("task-modern");
        assertThat(modernOutputs).containsEntry("source", "modern");
    }

    // Test Output implementation
    @Builder
    @Getter
    private static class TestOutput implements Output {
        private final String message;
        private final Integer count;
        private final Boolean success;
    }
}
