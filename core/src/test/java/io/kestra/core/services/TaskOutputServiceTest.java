package io.kestra.core.services;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunWithOutput;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.repositories.TaskOutputRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
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

    @Test
    @ExecuteFlow("flows/valids/each-switch.yaml")
    void outputsEachSwitch(Execution execution) throws JsonProcessingException {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(12);

        Map<String, Object> outputs = taskOutputService.computeOutputs(execution);
        assertThat(outputs).hasSize(10);
        String json = JacksonMapper.ofJson().writeValueAsString(outputs);
        assertThat(json).isEqualTo(
            "{\"2-1_each\":{},\"2_each\":{},\"2_end\":{\"value\":\"2_end\"},\"2-1_switch-letter-a\":{\"a\":{\"value\":\"2-1_switch-letter-a\"}},\"2-1_switch-letter-b\":{\"b\":{\"value\":\"2-1_switch-letter-b\"}},\"2-1-1_switch\":{\"b\":{\"1\":{\"defaults\":false,\"value\":\"1\"},\"2\":{\"defaults\":false,\"value\":\"2\"}}},\"2-1_switch\":{\"a\":{\"defaults\":false,\"value\":\"a\"},\"b\":{\"defaults\":false,\"value\":\"b\"}},\"2-1-1_switch-number-1\":{\"b\":{\"1\":{\"value\":\"1\"}}},\"t1\":{\"value\":\"t1\"},\"2-1-1_switch-number-2\":{\"b\":{\"2\":{\"value\":\"2 b\"}}}}"
        );
    }

    @Test
    @ExecuteFlow("flows/valids/each-object-in-list.yaml")
    void outputsEachObjectInList(Execution execution) throws JsonProcessingException {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(8);

        Map<String, Object> outputs = taskOutputService.computeOutputs(execution);
        assertThat(outputs).hasSize(5);

        String json = JacksonMapper.ofJson().writeValueAsString(outputs);
        assertThat(json).isEqualTo(
            "{\"not-json\":{\"value 1\":{\"value\":\"not-json > STRING > value 1\"}},\"2_end\":{\"value\":\"2_end\"},\"1_each\":{},\"json\":{\"{\\\"value\\\":\\\"my-value\\\",\\\"key\\\":\\\"my-key\\\"}\":{\"value\":\"json > JSON > [\\\"my-key\\\"] > [\\\"my-value\\\"]\"},\"{\\\"value\\\":{\\\"sub\\\":1,\\\"bool\\\":true},\\\"key\\\":\\\"my-complex\\\"}\":{\"value\":\"json > JSON > [\\\"my-complex\\\"] > [{\\\"sub\\\":1,\\\"bool\\\":true}]\"}},\"is-json\":{\"value 1\":{\"defaults\":false,\"value\":\"false\"},\"{\\\"value\\\":\\\"my-value\\\",\\\"key\\\":\\\"my-key\\\"}\":{\"defaults\":true,\"value\":\"true\"},\"{\\\"value\\\":{\\\"sub\\\":1,\\\"bool\\\":true},\\\"key\\\":\\\"my-complex\\\"}\":{\"defaults\":true,\"value\":\"true\"}}}"
        );
    }

    @Test
    @ExecuteFlow("flows/valids/if-in-flowable.yaml")
    void outputsIfInFlowable(Execution execution) throws JsonProcessingException {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(8);

        Map<String, Object> outputs = taskOutputService.computeOutputs(execution);
        assertThat(outputs).hasSize(4);

        String json = JacksonMapper.ofJson().writeValueAsString(outputs);
        assertThat(json).isEqualTo(
            "{\"after_if\":{\"value 2\":{\"value\":\"After if: value 2\"}},\"before_if\":{\"value 1\":{\"value\":\"Before if: value 1\"},\"value 2\":{\"value\":\"Before if: value 2\"},\"value 3\":{\"value\":\"Before if: value 3\"}},\"for_each\":{},\"if\":{\"value 1\":{\"evaluationResult\":false},\"value 2\":{\"evaluationResult\":true},\"value 3\":{\"evaluationResult\":false}}}"
        );
    }

    @Test
    @ExecuteFlow("flows/valids/waitfor-multiple-tasks.yaml")
    void outputsWaitForMultipleTasks(Execution execution) throws JsonProcessingException {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(3);

        Map<String, Object> outputs = taskOutputService.computeOutputs(execution);
        assertThat(outputs).hasSize(3);

        String json = JacksonMapper.ofJson().writeValueAsString(outputs);
        assertThat(json).isEqualTo("{\"output_values\":{\"values\":{\"count\":\"4\"}},\"echo\":{},\"waitfor\":{\"iterationCount\":3}}");
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

    // Test Output implementation
    @Builder
    @Getter
    private static class TestOutput implements Output {
        private final String message;
        private final Integer count;
        private final Boolean success;
    }
}
