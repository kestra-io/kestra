package io.kestra.core.repositories;

import java.nio.charset.StandardCharsets;
import java.util.*;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskOutput;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest(transactional = false)
public abstract class AbstractTaskOutputRepositoryTest {
    @Inject
    protected TaskOutputRepositoryInterface taskOutputRepository;

    private Execution createExecution(String tenant, String executionId, String... taskRunId) {
        List<TaskRun> taskRuns = taskRunId == null ? Collections.emptyList()
            : Arrays.asList(taskRunId).stream()
                .map(id -> TaskRun.builder().id(id).build())
                .toList();
        return Execution.builder()
            .id(executionId)
            .tenantId(tenant)
            .namespace("io.kestra.unittest")
            .flowId("test-flow")
            .flowRevision(1)
            .state(new State())
            .taskRunList(taskRuns)
            .build();
    }

    @Test
    void should_save_and_find_by_id() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String taskRunId = IdUtils.create();
        String executionId = IdUtils.create();
        byte[] value = "test output value".getBytes(StandardCharsets.UTF_8);
        String uri = "kestra://outputs/" + executionId + "/" + taskRunId;

        TaskOutput taskOutput = new TaskOutput(taskRunId, tenant, executionId, value, uri);

        TaskOutput saved = taskOutputRepository.save(taskOutput);
        assertThat(saved).isNotNull();
        assertThat(saved.taskRunId()).isEqualTo(taskRunId);
        assertThat(saved.tenantId()).isEqualTo(tenant);
        assertThat(saved.executionId()).isEqualTo(executionId);
        assertThat(saved.value()).isEqualTo(value);
        assertThat(saved.uri()).isEqualTo(uri);

        Optional<TaskOutput> found = taskOutputRepository.findById(tenant, taskRunId);
        assertThat(found).isPresent();
        assertThat(found.get().taskRunId()).isEqualTo(taskRunId);
        assertThat(found.get().tenantId()).isEqualTo(tenant);
        assertThat(found.get().executionId()).isEqualTo(executionId);
        assertThat(found.get().value()).isEqualTo(value);
        assertThat(found.get().uri()).isEqualTo(uri);
    }

    @Test
    void should_return_empty_when_not_found() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String taskRunId = IdUtils.create();

        Optional<TaskOutput> found = taskOutputRepository.findById(tenant, taskRunId);
        assertThat(found).isEmpty();
    }

    @Test
    void should_find_by_execution() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = IdUtils.create();
        String taskRunId1 = IdUtils.create();
        String taskRunId2 = IdUtils.create();
        String taskRunId3 = IdUtils.create();

        Execution execution = createExecution(tenant, executionId, taskRunId1, taskRunId2, taskRunId3);

        byte[] value1 = "output 1".getBytes(StandardCharsets.UTF_8);
        byte[] value2 = "output 2".getBytes(StandardCharsets.UTF_8);
        byte[] value3 = "output 3".getBytes(StandardCharsets.UTF_8);

        String uri1 = "kestra://outputs/" + executionId + "/" + taskRunId1;
        String uri2 = "kestra://outputs/" + executionId + "/" + taskRunId2;
        String uri3 = "kestra://outputs/" + executionId + "/" + taskRunId3;

        TaskOutput taskOutput1 = new TaskOutput(taskRunId1, tenant, executionId, value1, uri1);
        TaskOutput taskOutput2 = new TaskOutput(taskRunId2, tenant, executionId, value2, uri2);
        TaskOutput taskOutput3 = new TaskOutput(taskRunId3, tenant, executionId, value3, uri3);

        taskOutputRepository.save(taskOutput1);
        taskOutputRepository.save(taskOutput2);
        taskOutputRepository.save(taskOutput3);

        List<TaskOutput> outputs = taskOutputRepository.findByExecution(execution);
        assertThat(outputs).hasSize(3);
        assertThat(outputs)
            .extracting(TaskOutput::taskRunId)
            .containsExactlyInAnyOrder(taskRunId1, taskRunId2, taskRunId3);
        assertThat(outputs)
            .extracting(TaskOutput::executionId)
            .containsOnly(executionId);
    }

    @Test
    void should_return_empty_list_when_no_outputs_for_execution() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = IdUtils.create();
        Execution execution = createExecution(tenant, executionId);

        List<TaskOutput> outputs = taskOutputRepository.findByExecution(execution);
        assertThat(outputs).isEmpty();
    }

    @Test
    void should_isolate_tenants() {
        String tenant1 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String tenant2 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String taskRunId = IdUtils.create();
        String executionId = IdUtils.create();
        byte[] value = "test output".getBytes(StandardCharsets.UTF_8);
        String uri = "kestra://outputs/" + executionId + "/" + taskRunId;

        TaskOutput taskOutput1 = new TaskOutput(taskRunId, tenant1, executionId, value, uri);
        taskOutputRepository.save(taskOutput1);

        Optional<TaskOutput> foundInTenant1 = taskOutputRepository.findById(tenant1, taskRunId);
        assertThat(foundInTenant1).isPresent();

        Optional<TaskOutput> foundInTenant2 = taskOutputRepository.findById(tenant2, taskRunId);
        assertThat(foundInTenant2).isEmpty();
    }

    @Test
    void should_isolate_executions() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId1 = IdUtils.create();
        String executionId2 = IdUtils.create();
        String taskRunId1 = IdUtils.create();
        String taskRunId2 = IdUtils.create();

        Execution execution1 = createExecution(tenant, executionId1, taskRunId1);
        Execution execution2 = createExecution(tenant, executionId2, taskRunId2);

        byte[] value1 = "output 1".getBytes(StandardCharsets.UTF_8);
        byte[] value2 = "output 2".getBytes(StandardCharsets.UTF_8);

        String uri1 = "kestra://outputs/" + executionId1 + "/" + taskRunId1;
        String uri2 = "kestra://outputs/" + executionId2 + "/" + taskRunId2;

        TaskOutput taskOutput1 = new TaskOutput(taskRunId1, tenant, executionId1, value1, uri1);
        TaskOutput taskOutput2 = new TaskOutput(taskRunId2, tenant, executionId2, value2, uri2);

        taskOutputRepository.save(taskOutput1);
        taskOutputRepository.save(taskOutput2);

        List<TaskOutput> outputsExec1 = taskOutputRepository.findByExecution(execution1);
        assertThat(outputsExec1).hasSize(1);
        assertThat(outputsExec1.getFirst().taskRunId()).isEqualTo(taskRunId1);

        List<TaskOutput> outputsExec2 = taskOutputRepository.findByExecution(execution2);
        assertThat(outputsExec2).hasSize(1);
        assertThat(outputsExec2.getFirst().taskRunId()).isEqualTo(taskRunId2);
    }

    @Test
    void should_update_existing_task_output() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String taskRunId = IdUtils.create();
        String executionId = IdUtils.create();
        Execution execution = createExecution(tenant, executionId, taskRunId);

        byte[] value1 = "initial value".getBytes(StandardCharsets.UTF_8);
        byte[] value2 = "updated value".getBytes(StandardCharsets.UTF_8);
        String uri = "kestra://outputs/" + executionId + "/" + taskRunId;

        TaskOutput taskOutput1 = new TaskOutput(taskRunId, tenant, executionId, value1, uri);
        taskOutputRepository.save(taskOutput1);

        TaskOutput taskOutput2 = new TaskOutput(taskRunId, tenant, executionId, value2, uri);
        taskOutputRepository.save(taskOutput2);

        Optional<TaskOutput> found = taskOutputRepository.findById(tenant, taskRunId);
        assertThat(found).isPresent();
        assertThat(found.get().value()).isEqualTo(value2);

        List<TaskOutput> outputs = taskOutputRepository.findByExecution(execution);
        assertThat(outputs).hasSize(1);
    }

    @Test
    protected void should_purge_task_output() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId1 = IdUtils.create();
        String executionId2 = IdUtils.create();

        String taskRunId1 = IdUtils.create();
        String taskRunId2 = IdUtils.create();

        byte[] value1 = "output 1".getBytes(StandardCharsets.UTF_8);
        byte[] value2 = "output 2".getBytes(StandardCharsets.UTF_8);

        String uri1 = "kestra://outputs/" + executionId1 + "/" + taskRunId1;
        String uri2 = "kestra://outputs/" + executionId2 + "/" + taskRunId2;

        TaskOutput taskOutput1 = new TaskOutput(taskRunId1, tenant, executionId1, value1, uri1);
        TaskOutput taskOutput2 = new TaskOutput(taskRunId2, tenant, executionId2, value2, uri2);

        taskOutputRepository.save(taskOutput1);
        taskOutputRepository.save(taskOutput2);

        int purged = taskOutputRepository.purgeByExecutionIds(List.of(executionId1, executionId2));
        assertThat(purged).isEqualTo(2);
    }
}