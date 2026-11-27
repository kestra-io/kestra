package io.kestra.core.storages;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageContextTest {

    @ParameterizedTest
    @ValueSource(strings = { "team.", "team..product", ".team" })
    void shouldRejectNamespaceWithAnEmptySegmentOnPathsMadeOfTheNamespaceAlone(String namespace) {
        StorageContext context = forNamespace(namespace);

        assertThatThrownBy(() -> StorageContext.namespaceFilePrefix(namespace)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(namespace);
        assertThatThrownBy(() -> StorageContext.kvPrefix(namespace)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StorageContext.namespaceRootUri(namespace)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StorageContext.executionsRootUri(namespace, "flowid")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> context.getStateStorePrefix("id", false, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> context.getCacheURI("cache", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldStillBuildExecutionScopedPathsForANamespaceWithAnEmptySegment() {
        StorageContext context = forNamespace("team.");

        assertThat(context.getExecutionStorageURI().getPath()).endsWith("/flowid/executions/executionid");
        assertThat(StorageContext.namespaceAsPath("team.")).isEqualTo("team/");
        assertThat(StorageContext.isInNamespaceFiles("team.", context.getExecutionStorageURI())).isFalse();
    }

    private static StorageContext forNamespace(String namespace) {
        return StorageContext.forExecution(
            Execution
                .builder()
                .tenantId("tenantId")
                .id("executionid")
                .namespace(namespace)
                .flowId("flowid")
                .build()
        );
    }

    @Test
    void shouldGetValidUriForFlowContext() {
        StorageContext context = StorageContext.forExecution(
            Execution
                .builder()
                .tenantId("tenantId")
                .id("executionid")
                .namespace("namespace")
                .flowId("flowid")
                .build()
        );
        assertThat(context.getFlowStorageURI()).isEqualTo(URI.create("///namespace/flowid"));
    }

    @Test
    void shouldGetValidUriForExecutionContext() {
        StorageContext context = StorageContext.forExecution(
            Execution
                .builder()
                .tenantId("tenantId")
                .id("executionid")
                .namespace("namespace")
                .flowId("flowid")
                .build()
        );
        assertThat(context.getExecutionStorageURI()).isEqualTo(URI.create("///namespace/flowid/executions/executionid"));
        assertThat(context.getContextStorageURI()).isEqualTo(URI.create("///namespace/flowid/executions/executionid"));
    }

    @Test
    void shouldGetValidUriForExecutionContextWithScheme() {
        StorageContext context = StorageContext.forExecution(
            Execution
                .builder()
                .tenantId("tenantId")
                .id("executionid")
                .namespace("namespace")
                .flowId("flowid")
                .build()
        );
        assertThat(context.getExecutionStorageURI("kestra")).isEqualTo(URI.create("kestra:///namespace/flowid/executions/executionid"));
        assertThat(context.getExecutionStorageURI("kestra://")).isEqualTo(URI.create("kestra:///namespace/flowid/executions/executionid"));
        assertThat(context.getContextStorageURI()).isEqualTo(URI.create("///namespace/flowid/executions/executionid"));
    }

    @Test
    void shouldGetValidURIForTaskContext() {
        StorageContext context = StorageContext.forTask(
            "???",
            "namespace",
            "flowid",
            "executionid",
            "taskid",
            "taskrun",
            null
        );

        assertThat(context.getExecutionStorageURI()).isEqualTo(URI.create("///namespace/flowid/executions/executionid"));
        assertThat(context.getContextStorageURI()).isEqualTo(URI.create("///namespace/flowid/executions/executionid/tasks/taskid/taskrun"));
    }

    @Test
    void shouldGetValidURIForTriggerContext() {
        StorageContext context = StorageContext.forTrigger(
            "???",
            "namespace",
            "flowid",
            "executionid",
            "triggerid"
        );

        assertThat(context.getExecutionStorageURI()).isEqualTo(URI.create("///namespace/flowid/executions/executionid"));
        assertThat(context.getContextStorageURI()).isEqualTo(URI.create("///namespace/flowid/executions/executionid/trigger/triggerid"));
    }

    @Test
    void shouldAcceptPathWhenInsideExecutionStorage() {
        Execution execution = execution("io.kestra.tests", "my-flow", "exec1");

        assertThat(StorageContext.isInExecutionStorage(execution, URI.create("kestra:///io/kestra/tests/my-flow/executions/exec1/tasks/t/out.txt"))).isTrue();
        assertThat(StorageContext.isInExecutionStorage(execution, URI.create("/io/kestra/tests/my-flow/executions/exec1/out.txt"))).isTrue();
    }

    @Test
    void shouldRejectPathWhenOutsideExecutionStorage() {
        Execution execution = execution("io.kestra.tests", "my-flow", "exec1");

        assertThat(StorageContext.isInExecutionStorage(execution, URI.create("kestra:///io/kestra/tests/other-flow/executions/exec1/out.txt"))).isFalse();
        assertThat(StorageContext.isInExecutionStorage(execution, URI.create("kestra:///other/my-flow/executions/exec1/out.txt"))).isFalse();
        assertThat(StorageContext.isInExecutionStorage(execution, URI.create("kestra:///io/kestra/tests/my-flow/executions/exec12/out.txt"))).isFalse();
        assertThat(StorageContext.isInExecutionStorage(execution, URI.create("kestra:///io/kestra/tests/my-flow/executions/exec1"))).isFalse();
    }

    private static Execution execution(String namespace, String flowId, String executionId) {
        return Execution.builder().id(executionId).namespace(namespace).flowId(flowId).build();
    }

    @Test
    void shouldGetNamespaceFilePrefix() {
        assertThat(StorageContext.namespaceFilePrefix("io.namespace")).isEqualTo("/io/namespace/_files");
    }

    @Test
    void shouldGetTaskCachePrefix() {
        assertThat(
            StorageContext.forFlow(
                Flow
                    .builder()
                    .tenantId(null)
                    .namespace("namespace")
                    .id("flowid")
                    .build()
            ).getCacheURI("taskid", null)
        ).isEqualTo(URI.create("/namespace/flowid/taskid/cache/cache.zip"));

        assertThat(
            StorageContext.forFlow(
                Flow
                    .builder()
                    .tenantId(null)
                    .namespace("namespace")
                    .id("flowid")
                    .build()
            ).getCacheURI("taskid", "value")
        ).isEqualTo(URI.create("/namespace/flowid/taskid/cache/7d04fd3bbbc0946dc06caf7356fdf051/cache.zip"));
    }

    @Test
    void shouldOnlyTrustTheTriggeringExecutionSetByAFlowOrSubflowTrigger() {
        Map<String, Object> ids = Map.of("namespace", "company.team", "flowId", "My Flow", "executionId", "exec1");

        assertThat(StorageContext.triggeringExecutionPrefix(Map.of("trigger", withContext(ids, "io.kestra.plugin.core.flow.Subflow"))))
            .isEqualTo("/company/team/my-flow/executions/exec1/");
        assertThat(StorageContext.triggeringExecutionPrefix(Map.of("trigger", withContext(ids, "io.kestra.plugin.core.trigger.Flow"))))
            .isEqualTo("/company/team/my-flow/executions/exec1/");
        assertThat(StorageContext.triggeringExecutionPrefix(Map.of("trigger", withContext(ids, "io.kestra.plugin.core.trigger.Webhook")))).isNull();
        assertThat(StorageContext.triggeringExecutionPrefix(Map.of("trigger", ids))).isNull();
        assertThat(StorageContext.triggeringExecutionPrefix(Map.of())).isNull();
    }

    private static Map<String, Object> withContext(Map<String, Object> ids, String type) {
        Map<String, Object> trigger = new HashMap<>(ids);
        trigger.put("_context", Map.of("id", "trigger", "type", type));
        return trigger;
    }
}
