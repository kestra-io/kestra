package io.kestra.core.storages;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

class StorageContextTest {

    @Test
    void shouldGetValidUriForFlowContext() {
        StorageContext context = StorageContext.forExecution(Execution
            .builder()
            .tenantId("tenantId")
            .id("executionid")
            .namespace("namespace")
            .flowId("flowid")
            .build()
        );
        assertThat(context.getFlowStorageURI(), is(URI.create("///namespace/flowid")));
    }

    @Test
    void shouldGetValidUriForExecutionContext() {
        StorageContext context = StorageContext.forExecution(Execution
            .builder()
                .tenantId("tenantId")
                .id("executionid")
                .namespace("namespace")
                .flowId("flowid")
            .build()
        );
        assertThat(context.getExecutionStorageURI(), is(URI.create("///namespace/flowid/executions/executionid")));
        assertThat(context.getContextStorageURI(), is(URI.create("///namespace/flowid/executions/executionid")));
    }

    @Test
    void shouldGetValidUriForExecutionContextWithScheme() {
        StorageContext context = StorageContext.forExecution(Execution
            .builder()
            .tenantId("tenantId")
            .id("executionid")
            .namespace("namespace")
            .flowId("flowid")
            .build()
        );
        assertThat(context.getExecutionStorageURI("kestra"), is(URI.create("kestra:///namespace/flowid/executions/executionid")));
        assertThat(context.getExecutionStorageURI("kestra://"), is(URI.create("kestra:///namespace/flowid/executions/executionid")));
        assertThat(context.getContextStorageURI(), is(URI.create("///namespace/flowid/executions/executionid")));
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

        assertThat(context.getExecutionStorageURI(), is(URI.create("///namespace/flowid/executions/executionid")));
        assertThat(context.getContextStorageURI(), is(URI.create("///namespace/flowid/executions/executionid/tasks/taskid/taskrun")));
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

        assertThat(context.getExecutionStorageURI(), is(URI.create("///namespace/flowid/executions/executionid")));
        assertThat(context.getContextStorageURI(), is(URI.create("///namespace/flowid/executions/executionid/trigger/triggerid")));
    }

    @Test
    void shouldGetNamespaceFilePrefix() {
        assertThat(StorageContext.namespaceFilePrefix("io.namespace"), is("/io/namespace/_files"));
    }

    @Test
    void shouldGetTaskCachePrefix() {
        assertThat(StorageContext.forFlow(Flow
                .builder()
                    .tenantId(null)
                    .namespace("namespace")
                    .id("flowid")
                .build()
            ).getCacheURI("taskid", null), is(URI.create("/namespace/flowid/taskid/cache/cache.zip")));

        assertThat(StorageContext.forFlow(Flow
            .builder()
            .tenantId(null)
            .namespace("namespace")
            .id("flowid")
            .build()
        ).getCacheURI("taskid", "value"), is(URI.create("/namespace/flowid/taskid/cache/7d04fd3bbbc0946dc06caf7356fdf051/cache.zip")));
    }
}