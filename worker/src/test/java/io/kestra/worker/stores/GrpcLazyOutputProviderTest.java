package io.kestra.worker.stores;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.controller.grpc.TaskOutputProviderServiceGrpc.TaskOutputProviderServiceBlockingStub;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskOutput;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.TaskOutputRepositoryInterface;
import io.kestra.core.runners.LazyOutputsMap;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.IdUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@KestraTest
class GrpcLazyOutputProviderTest extends AbstractGrpcMetaStoreTest {

    private static final ObjectMapper ION_MAPPER = JacksonMapper.ofIon();

    @Inject
    TaskOutputProviderServiceBlockingStub taskOutputStub;

    @Inject
    ExecutionRepositoryInterface executionRepository;

    @Inject
    TaskOutputRepositoryInterface taskOutputRepository;

    private GrpcLazyOutputProvider grpcProvider;

    // test execution state
    private String tenantId;
    private String executionId;

    @Override
    protected void initClientStore() {
        // provider is created per test in setUp()
    }

    private void setUp(Execution execution) {
        this.tenantId = execution.getTenantId();
        this.executionId = execution.getId();
        this.grpcProvider = new GrpcLazyOutputProvider(taskOutputStub, clientWorkerInfo(), tenantId, executionId);
    }

    @Test
    void shouldReturnOutputsForTaskViaGrpc() throws JsonProcessingException {
        // Given
        String taskRunId = IdUtils.create();
        String taskId = "myTask";

        Execution execution = createExecution(taskRunId, taskId, null);
        executionRepository.save(execution);
        saveOutput(taskRunId, execution, Map.of("value", "hello"));
        setUp(execution);

        // When
        Map<String, Object> outputs = grpcProvider.computeOutputsForTask(taskId);

        // Then
        assertThat(outputs).isNotNull();
        assertThat(outputs).containsEntry("value", "hello");
    }

    @Test
    void shouldReturnAllOutputsViaGrpc() throws JsonProcessingException {
        // Given
        String taskRunId1 = IdUtils.create();
        String taskRunId2 = IdUtils.create();

        Execution execution = createExecution(
            List.of(
                taskRun(taskRunId1, "task1", null),
                taskRun(taskRunId2, "task2", null)
            )
        );
        executionRepository.save(execution);
        saveOutput(taskRunId1, execution, Map.of("value", "one"));
        saveOutput(taskRunId2, execution, Map.of("value", "two"));
        setUp(execution);

        // When
        Map<String, Object> allOutputs = grpcProvider.computeOutputs();

        // Then
        assertThat(allOutputs).containsKey("task1");
        assertThat(allOutputs).containsKey("task2");
    }

    @Test
    void shouldReturnTaskIdsWithOutputViaGrpc() throws JsonProcessingException {
        // Given
        String taskRunId1 = IdUtils.create();
        String taskRunId2 = IdUtils.create();

        Execution execution = createExecution(
            List.of(
                taskRun(taskRunId1, "taskA", null),
                taskRun(taskRunId2, "taskB", null)
            )
        );
        executionRepository.save(execution);
        saveOutput(taskRunId1, execution, Map.of("x", 1));
        // taskB has no output
        setUp(execution);

        // When
        Set<String> taskIds = grpcProvider.findTaskIdsWithOutput();

        // Then
        assertThat(taskIds).contains("taskA");
        assertThat(taskIds).doesNotContain("taskB");
    }

    @Test
    void shouldReturnValueToTaskIdsViaGrpc() {
        // Given
        String taskRunId1 = IdUtils.create();
        String taskRunId2 = IdUtils.create();

        Execution execution = createExecution(
            List.of(
                taskRun(taskRunId1, "loopTask", "iter_1"),
                taskRun(taskRunId2, "loopTask", "iter_2")
            )
        );
        executionRepository.save(execution);
        setUp(execution);

        // When
        Map<String, List<String>> valueToTaskIds = grpcProvider.valueToTaskIds();

        // Then
        assertThat(valueToTaskIds).containsKey("iter_1");
        assertThat(valueToTaskIds).containsKey("iter_2");
        assertThat(valueToTaskIds.get("iter_1")).contains("loopTask");
    }

    @Test
    void shouldReturnEmptyWhenExecutionNotFound() {
        // Given
        grpcProvider = new GrpcLazyOutputProvider(taskOutputStub, clientWorkerInfo(), "nonexistent", "nonexistent");

        // When
        Map<String, Object> outputs = grpcProvider.computeOutputs();
        Set<String> taskIds = grpcProvider.findTaskIdsWithOutput();
        Map<String, List<String>> valueToTaskIds = grpcProvider.valueToTaskIds();

        // Then
        assertThat(outputs).isEmpty();
        assertThat(taskIds).isEmpty();
        assertThat(valueToTaskIds).isEmpty();
    }

    @Test
    void shouldCacheFindTaskIdsWithOutput() throws JsonProcessingException {
        // Given
        String taskRunId = IdUtils.create();
        Execution execution = createExecution(taskRunId, "cachedTask", null);
        executionRepository.save(execution);
        saveOutput(taskRunId, execution, Map.of("k", "v"));
        setUp(execution);

        // When
        Set<String> first = grpcProvider.findTaskIdsWithOutput();
        Set<String> second = grpcProvider.findTaskIdsWithOutput();

        // Then - same reference means cached
        assertThat(first).isSameAs(second);
        assertThat(first).contains("cachedTask");
    }

    @Test
    void shouldOnlyFetchAccessedTaskOutputViaGrpc() throws JsonProcessingException {
        // Given - two tasks with outputs
        String taskRunId1 = IdUtils.create();
        String taskRunId2 = IdUtils.create();

        Execution execution = createExecution(
            List.of(
                taskRun(taskRunId1, "accessed", null),
                taskRun(taskRunId2, "notAccessed", null)
            )
        );
        executionRepository.save(execution);
        saveOutput(taskRunId1, execution, Map.of("value", "fetched"));
        saveOutput(taskRunId2, execution, Map.of("value", "should-not-be-fetched"));

        // Use a spy to track which methods are called on the provider
        GrpcLazyOutputProvider providerSpy = spy(
            new GrpcLazyOutputProvider(taskOutputStub, clientWorkerInfo(), execution.getTenantId(), execution.getId())
        );
        LazyOutputsMap lazyMap = new LazyOutputsMap(providerSpy);

        // When - only access "accessed" task output
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) lazyMap.get("accessed");

        // Then - correct output fetched
        assertThat(result).containsEntry("value", "fetched");

        // And - only computeOutputsForTask("accessed") was called, never for "notAccessed"
        verify(providerSpy).computeOutputsForTask("accessed");
        verify(providerSpy, never()).computeOutputsForTask("notAccessed");
        // And - computeOutputs (full load) was never called
        verify(providerSpy, never()).computeOutputs();
    }

    // -- helpers --

    private Execution createExecution(String taskRunId, String taskId, String value) {
        return createExecution(List.of(taskRun(taskRunId, taskId, value)));
    }

    private Execution createExecution(List<TaskRun> taskRuns) {
        String tenant = IdUtils.create();
        String execId = IdUtils.create();
        return Execution.builder()
            .id(execId)
            .tenantId(tenant)
            .namespace("io.kestra.tests")
            .flowId("test-flow")
            .flowRevision(1)
            .state(new State().withState(State.Type.SUCCESS))
            .taskRunList(taskRuns.stream()
                .map(tr -> tr.toBuilder()
                    .executionId(execId)
                    .tenantId(tenant)
                    .state(new State().withState(State.Type.SUCCESS))
                    .build())
                .toList())
            .build();
    }

    private static TaskRun taskRun(String id, String taskId, String value) {
        return TaskRun.builder()
            .id(id)
            .taskId(taskId)
            .value(value)
            .build();
    }

    private void saveOutput(String taskRunId, Execution execution, Map<String, Object> outputMap) throws JsonProcessingException {
        byte[] value = ION_MAPPER.writeValueAsBytes(outputMap);
        taskOutputRepository.save(new TaskOutput(taskRunId, execution.getTenantId(), execution.getId(), value, null));
    }
}
