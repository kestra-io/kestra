package io.kestra.plugin.core.storage;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.ExecutableUtils;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class PurgeCurrentExecutionFilesTest {
    @Inject
    private TestRunContextFactory runContextFactory;

    @Inject
    private StorageInterface storageInterface;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Test
    void run() throws Exception {
        var flow = Flow.builder()
            .namespace("namespace")
            .id("flowId")
            .tenantId(MAIN_TENANT)
            .build();
        var runContext = runContextFactory.of(
            flow, Map.of(
                "execution", Map.of("id", "executionId"),
                "task", Map.of("id", "taskId"),
                "taskrun", Map.of("id", "taskRunId")
            )
        );
        var file = runContext.workingDir().createFile("test.txt", "Hello World".getBytes());
        runContext.storage().putFile(file.toFile());

        var purge = PurgeCurrentExecutionFiles.builder()
            .build();
        var output = purge.run(runContext);

        assertThat(output.getUris().size()).isEqualTo(2);
    }

    @Test
    void shouldPurgeCurrentAndChildExecutionFilesRecursivelyWhenIncludeChildExecutionsIsTrue() throws Exception {
        String parentExecId = IdUtils.create();
        String childExecId = IdUtils.create();
        String grandChildExecId = IdUtils.create();

        var parentFlow = Flow.builder()
            .namespace("parent.namespace")
            .id("parent-flow")
            .tenantId(MAIN_TENANT)
            .build();

        var parentExecution = Execution.builder()
            .id(parentExecId)
            .tenantId(MAIN_TENANT)
            .namespace(parentFlow.getNamespace())
            .flowId(parentFlow.getId())
            .flowRevision(1)
            .state(new State().withState(State.Type.SUCCESS))
            .build();
        executionRepository.save(parentExecution);

        var childExecution = Execution.builder()
            .id(childExecId)
            .tenantId(MAIN_TENANT)
            .namespace("child.namespace")
            .flowId("child-flow")
            .flowRevision(1)
            .state(new State().withState(State.Type.SUCCESS))
            .trigger(ExecutionTrigger.builder()
                .id("subflow")
                .type(ExecutableUtils.SUBFLOW_TRIGGER_TYPE)
                .variables(Map.of("executionId", parentExecId))
                .build())
            .build();
        executionRepository.save(childExecution);

        var grandChildExecution = Execution.builder()
            .id(grandChildExecId)
            .tenantId(MAIN_TENANT)
            .namespace("grandchild.namespace")
            .flowId("grandchild-flow")
            .flowRevision(1)
            .state(new State().withState(State.Type.SUCCESS))
            .trigger(ExecutionTrigger.builder()
                .id("subflow")
                .type(ExecutableUtils.SUBFLOW_TRIGGER_TYPE)
                .variables(Map.of("executionId", childExecId))
                .build())
            .build();
        executionRepository.save(grandChildExecution);

        URI parentFile = putExecutionFile(parentExecution, "/tasks/parent-task/file.txt", "parent content");
        URI childFile = putExecutionFile(childExecution, "/tasks/child-task/file.txt", "child content");
        URI grandChildFile = putExecutionFile(grandChildExecution, "/tasks/grandchild-task/file.txt", "grandchild content");

        assertThat(storageInterface.exists(MAIN_TENANT, parentExecution.getNamespace(), parentFile)).isTrue();
        assertThat(storageInterface.exists(MAIN_TENANT, childExecution.getNamespace(), childFile)).isTrue();
        assertThat(storageInterface.exists(MAIN_TENANT, grandChildExecution.getNamespace(), grandChildFile)).isTrue();

        var runContext = runContextFactory.of(parentFlow, parentExecution);

        var purge = PurgeCurrentExecutionFiles.builder()
            .includeChildExecutions(Property.ofValue(true))
            .build();
        var output = purge.run(runContext);

        assertThat(output.getUris()).contains(parentFile, childFile, grandChildFile);
        assertThat(storageInterface.exists(MAIN_TENANT, parentExecution.getNamespace(), parentFile)).isFalse();
        assertThat(storageInterface.exists(MAIN_TENANT, childExecution.getNamespace(), childFile)).isFalse();
        assertThat(storageInterface.exists(MAIN_TENANT, grandChildExecution.getNamespace(), grandChildFile)).isFalse();
    }

    @Test
    void shouldNotPurgeChildExecutionFilesWhenIncludeChildExecutionsIsFalse() throws Exception {
        String parentExecId = IdUtils.create();
        String childExecId = IdUtils.create();

        var parentFlow = Flow.builder()
            .namespace("parent.namespace")
            .id("parent-flow")
            .tenantId(MAIN_TENANT)
            .build();

        var parentExecution = Execution.builder()
            .id(parentExecId)
            .tenantId(MAIN_TENANT)
            .namespace(parentFlow.getNamespace())
            .flowId(parentFlow.getId())
            .flowRevision(1)
            .state(new State().withState(State.Type.SUCCESS))
            .build();
        executionRepository.save(parentExecution);

        var childExecution = Execution.builder()
            .id(childExecId)
            .tenantId(MAIN_TENANT)
            .namespace("child.namespace")
            .flowId("child-flow")
            .flowRevision(1)
            .state(new State().withState(State.Type.SUCCESS))
            .trigger(ExecutionTrigger.builder()
                .id("subflow")
                .type(ExecutableUtils.SUBFLOW_TRIGGER_TYPE)
                .variables(Map.of("executionId", parentExecId))
                .build())
            .build();
        executionRepository.save(childExecution);

        URI parentFile = putExecutionFile(parentExecution, "/tasks/parent-task/file.txt", "parent content");
        URI childFile = putExecutionFile(childExecution, "/tasks/child-task/file.txt", "child content");

        var runContext = runContextFactory.of(parentFlow, parentExecution);

        var purge = PurgeCurrentExecutionFiles.builder()
            .includeChildExecutions(Property.ofValue(false))
            .build();
        var output = purge.run(runContext);

        assertThat(output.getUris()).contains(parentFile);
        assertThat(output.getUris()).doesNotContain(childFile);
        assertThat(storageInterface.exists(MAIN_TENANT, parentExecution.getNamespace(), parentFile)).isFalse();
        assertThat(storageInterface.exists(MAIN_TENANT, childExecution.getNamespace(), childFile)).isTrue();
    }

    private URI putExecutionFile(Execution execution, String relativePath, String content) throws Exception {
        URI fileUri = URI.create(StorageContext.forExecution(execution).getExecutionStorageURI() + relativePath);
        return storageInterface.put(
            execution.getTenantId(),
            execution.getNamespace(),
            fileUri,
            new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))
        );
    }
}
