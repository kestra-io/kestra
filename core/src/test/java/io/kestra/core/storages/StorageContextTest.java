package io.kestra.core.storages;

import java.net.URI;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageContextTest {

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
        assertThat(context.getExecutionStorageURI("kestra")).isEqualTo(URI.create("kestra://namespace/flowid/executions/executionid"));
        assertThat(context.getExecutionStorageURI("kestra://")).isEqualTo(URI.create("kestra://namespace/flowid/executions/executionid"));
        assertThat(context.getExecutionStorageURI("kestra").getAuthority()).isEqualTo("namespace");
        assertThat(context.getExecutionStorageURI("kestra").getPath()).isEqualTo("/flowid/executions/executionid");
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
    void shouldRenderCanonicalUriAndKeepLegacyUriOnTheSamePath() {
        String path = "/namespace/folder/sub/script.py";
        URI canonical = StorageContext.toKestraUri(path);
        URI legacy = URI.create("kestra:///namespace/folder/sub/script.py");

        assertThat(canonical).isEqualTo(URI.create("kestra://namespace/folder/sub/script.py"));
        assertThat(canonical.getAuthority()).isEqualTo("namespace");
        assertThat(canonical.getHost()).isEqualTo("namespace");
        assertThat(canonical.getPath()).isEqualTo("/folder/sub/script.py");
        assertThat(legacy.getAuthority()).isNull();
        assertThat(StorageContext.logicalPath(canonical)).isEqualTo(path);
        assertThat(StorageContext.logicalPath(legacy)).isEqualTo(path);
        assertThat(StorageContext.toKestraUri(legacy)).isEqualTo(canonical);
        assertThat(StorageContext.legacyKestraUri(canonical)).isEqualTo(legacy);

        URI underscore = StorageContext.toKestraUri("/files_by_prefix/folder/file.txt");
        assertThat(underscore).isEqualTo(URI.create("kestra://files_by_prefix/folder/file.txt"));
        assertThat(underscore.getAuthority()).isEqualTo("files_by_prefix");
        assertThat(underscore.getHost()).isNull();
        assertThat(StorageContext.logicalPath(underscore)).isEqualTo("/files_by_prefix/folder/file.txt");

        URI spaced = StorageContext.toKestraUri("/namespace/a b.txt");
        assertThat(spaced.toString()).isEqualTo("kestra://namespace/a%20b.txt");
        assertThat(StorageContext.logicalPath(spaced)).isEqualTo("/namespace/a b.txt");
        assertThat(StorageContext.toKestraUri("/namespace/a+b.txt").toString()).isEqualTo("kestra://namespace/a+b.txt");
        assertThat(StorageContext.toKestraUri("/namespace/report#1.csv").toString()).isEqualTo("kestra://namespace/report%231.csv");

        assertThat(StorageContext.toKestraUri("/")).isEqualTo(URI.create("kestra:///"));
        assertThat(StorageContext.toKestraUri("/namespace/")).isEqualTo(URI.create("kestra://namespace/"));
        assertThat(StorageContext.logicalPath(URI.create("kestra:/namespace/folder/file"))).isEqualTo("/namespace/folder/file");
        assertThat(StorageContext.logicalPath(URI.create("kestra://../etc/passwd"))).isEqualTo("/../etc/passwd");
    }

    @Test
    void shouldRejectKestraUriWithUserInfoOrPort() {
        assertThatThrownBy(() -> StorageContext.logicalPath(URI.create("kestra://namespace:80/file")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StorageContext.logicalPath(URI.create("kestra://user@namespace/file")))
            .isInstanceOf(IllegalArgumentException.class);
    }
}