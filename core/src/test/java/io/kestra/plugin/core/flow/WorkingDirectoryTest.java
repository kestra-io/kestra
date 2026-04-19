package io.kestra.plugin.core.flow;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ExecutionMode;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.junit.annotations.FlakyTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.common.EncryptedString;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.FilesService;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.core.services.TaskOutputService;
import io.kestra.core.storages.InternalStorage;
import io.kestra.core.storages.NamespaceFactory;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.*;
import lombok.experimental.SuperBuilder;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest(startRunner = true)
@org.junit.jupiter.api.parallel.Execution(ExecutionMode.SAME_THREAD)
public class WorkingDirectoryTest {
    @Inject
    Suite suite;

    @Inject
    RunContextFactory runContextFactory;

    @Inject
    TestRunnerUtils runnerUtils;

    @Test
    @LoadFlows({ "flows/valids/working-directory.yaml" })
    void success() throws TimeoutException, QueueException, InternalException {
        suite.success(runnerUtils);
    }

    @Test
    @LoadFlows(value = { "flows/valids/working-directory.yaml" }, tenantId = "tenant1")
    void failed() throws TimeoutException, QueueException {
        suite.failed("tenant1", runnerUtils);
    }

    @Test
    @LoadFlows({ "flows/valids/working-directory-each.yaml" })
    void each() throws TimeoutException, QueueException, InternalException {
        suite.each(runnerUtils);
    }

    @Test
    @LoadFlows({ "flows/valids/working-directory-cache.yml" })
    void cache() throws TimeoutException, IOException, QueueException, InternalException {
        suite.cache(runnerUtils);
    }

    @Test
    @LoadFlows({ "flows/valids/working-directory-taskrun.yml" })
    void taskrun() throws TimeoutException, InternalException, QueueException {
        suite.taskRun(runnerUtils);
    }

    @Test
    @LoadFlows({ "flows/valids/working-directory-taskrun-nested.yml" })
    void taskrunNested() throws TimeoutException, InternalException, QueueException {
        suite.taskRunNested(runnerUtils);
    }

    @Test
    @LoadFlows({ "flows/valids/working-directory-namespace-files.yaml" })
    void namespaceFiles() throws TimeoutException, IOException, QueueException, URISyntaxException, InternalException {
        suite.namespaceFiles(runnerUtils);
    }

    @Test
    @LoadFlows({ "flows/valids/working-directory-namespace-files-with-namespaces.yaml" })
    void namespaceFilesWithNamespace() throws TimeoutException, IOException, QueueException, URISyntaxException, InternalException {
        suite.namespaceFilesWithNamespaces(runnerUtils);
    }

    @Test
    @LoadFlows({ "flows/valids/working-directory-inputs.yml" })
    void inputFiles() throws Exception {
        suite.inputFiles(runnerUtils);
    }

    // FIXME can be moved back to regular @Test once https://github.com/kestra-io/kestra/issues/13134 is handled
    @FlakyTest
    @LoadFlows(value = { "flows/valids/working-directory-outputs.yml" }, tenantId = "output")
    void outputFiles() throws Exception {
        suite.outputFiles("output", runnerUtils);
    }

    @Test
    @LoadFlows({ "flows/valids/working-directory-taskrun-encrypted.yml" })
    void encryption() throws Exception {
        suite.encryption(runnerUtils, runContextFactory);
    }

    @Test
    @LoadFlows({"flows/valids/working-directory-invalid-when.yaml"})
    void invalidWhen() throws Exception {
        suite.invalidWhen(runnerUtils);
    }

    @Singleton
    public static class Suite {
        @Inject
        StorageInterface storageInterface;
        @Inject
        NamespaceFactory namespaceFactory;
        @Inject
        TaskOutputService taskOutputService;

        public void success(TestRunnerUtils runnerUtils) throws TimeoutException, QueueException, io.kestra.core.exceptions.InternalException {
            Execution execution = runnerUtils.runOne(
                MAIN_TENANT, "io.kestra.tests", "working-directory", null,
                (f, e) -> ImmutableMap.of("failed", "false"), Duration.ofSeconds(60)
            );

            assertThat(execution.getTaskRunList()).hasSize(4);
            assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
            assertThat((String) taskOutputService.getOutputs(execution.getTaskRunList().get(3)).get("value")).startsWith("kestra://");
        }

        public void failed(String tenantId, TestRunnerUtils runnerUtils) throws TimeoutException, QueueException {
            Execution execution = runnerUtils.runOne(
                tenantId, "io.kestra.tests", "working-directory", null,
                (f, e) -> ImmutableMap.of("failed", "true"), Duration.ofSeconds(60)
            );

            assertThat(execution.getTaskRunList()).hasSize(3);
            assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
            assertThat(execution.findTaskRunsByTaskId("error-t1")).hasSize(1);
        }

        public void each(TestRunnerUtils runnerUtils) throws TimeoutException, QueueException, io.kestra.core.exceptions.InternalException {
            Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "working-directory-each", Duration.ofSeconds(60));

            assertThat(execution.getTaskRunList()).hasSize(8);
            assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
            assertThat((String) taskOutputService.getOutputs(execution.findTaskRunsByTaskId("2_end").getFirst()).get("value")).startsWith("kestra://");
        }

        @SuppressWarnings("unchecked")
        public void outputFiles(String tenantId, TestRunnerUtils runnerUtils) throws TimeoutException, IOException, QueueException, io.kestra.core.exceptions.InternalException {

            Execution execution = runnerUtils.runOne(tenantId, "io.kestra.tests", "working-directory-outputs");

            assertThat(execution.getTaskRunList()).hasSize(2);
            assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

            TaskRun taskRun = execution.getTaskRunList().getFirst();
            System.out.println(taskRun.getTaskId());
            Map<String, Object> outputs = taskOutputService.getOutputs(taskRun);
            assertThat(outputs).containsKey("outputFiles");

            StorageContext storageContext = StorageContext.forTask(taskRun);
            InternalStorage storage = new InternalStorage(
                null,
                storageContext,
                storageInterface,
                null,
                namespaceFactory
            );

            URI uri = ((Map<String, String>) outputs.get("outputFiles")).values()
                .stream()
                .map(URI::create)
                .toList().getFirst();
            assertThat(new String(storage.getFile(uri).readAllBytes())).isEqualTo("Hello World");
        }

        @SuppressWarnings("unchecked")
        public void inputFiles(TestRunnerUtils runnerUtils) throws TimeoutException, IOException, QueueException, io.kestra.core.exceptions.InternalException {

            Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "working-directory-inputs");

            assertThat(execution.getTaskRunList()).hasSize(2);
            assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

            StorageContext storageContext = StorageContext.forTask(execution.getTaskRunList().get(1));
            InternalStorage storage = new InternalStorage(
                null,
                storageContext,
                storageInterface,
                null,
                namespaceFactory
            );

            TaskRun taskRun = execution.getTaskRunList().get(1);
            Map<String, Object> outputs = taskOutputService.getOutputs(taskRun);
            assertThat(outputs).containsKey("uris");

            URI uri = URI.create(((Map<String, String>) outputs.get("uris")).get("input.txt"));

            assertTrue(uri.toString().endsWith("input.txt"));
            assertThat(new String(storage.getFile(uri).readAllBytes())).isEqualTo("Hello World");
        }

        @SuppressWarnings({ "unchecked", "OptionalGetWithoutIsPresent" })
        public void cache(TestRunnerUtils runnerUtils) throws TimeoutException, IOException, QueueException, io.kestra.core.exceptions.InternalException {
            // make sure the cache didn't exist
            StorageContext storageContext = StorageContext.forFlow(
                Flow
                    .builder()
                    .namespace("io.kestra.tests")
                    .id("working-directory-cache")
                    .tenantId(MAIN_TENANT)
                    .build()
            );
            InternalStorage storage = new InternalStorage(
                null,
                storageContext,
                storageInterface,
                null,
                namespaceFactory
            );
            storage.deleteCacheFile("workingDir", null);

            URI cacheURI = storageContext.getCacheURI("workingdir", null);
            assertFalse(storageInterface.exists(MAIN_TENANT, null, cacheURI));

            Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "working-directory-cache");

            assertThat(execution.getTaskRunList()).hasSize(3);
            assertThat(
                taskOutputService.getOutputs(
                    execution.getTaskRunList().stream()
                        .filter(t -> t.getTaskId().equals("exists"))
                        .findFirst().get()
                )
            ).containsAllEntriesOf(Map.of("uris", Collections.emptyMap()));
            assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
            assertTrue(storageInterface.exists(MAIN_TENANT, null, cacheURI));

            // a second run should use the cache so the task `exists` should output the cached file
            execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "working-directory-cache");

            assertThat(execution.getTaskRunList()).hasSize(3);
            assertThat(
                ((Map<String, String>) taskOutputService.getOutputs(
                    execution.getTaskRunList().stream()
                        .filter(t -> t.getTaskId().equals("exists"))
                        .findFirst().get()
                )
                    .get("uris"))
                    .containsKey("hello.txt")
            ).isTrue();
            assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        }

        public void taskRun(TestRunnerUtils runnerUtils) throws TimeoutException, InternalException, QueueException {
            Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "working-directory-taskrun");

            assertThat(execution.getTaskRunList()).hasSize(3);
            assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
            assertThat(((String) taskOutputService.getOutputs(execution.findTaskRunByTaskIdAndValue("log-taskrun", List.of("1"))).get("value"))).contains("1");
        }

        public void taskRunNested(TestRunnerUtils runnerUtils) throws TimeoutException, InternalException, QueueException {
            Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "working-directory-taskrun-nested");

            assertThat(execution.getTaskRunList()).hasSize(6);
            assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
            assertThat(((String) taskOutputService.getOutputs(execution.findTaskRunByTaskIdAndValue("log-workerparent", List.of("1"))).get("value")))
                .contains("{\"taskrun\":{\"value\":\"1\"},\"task\":{\"id\":\"seq\"}}");
        }

        public void namespaceFiles(TestRunnerUtils runnerUtils) throws TimeoutException, IOException, QueueException, URISyntaxException, io.kestra.core.exceptions.InternalException {
            put("/test/a/b/c/1.txt", "first");
            put("/a/b/c/2.txt", "second");
            put("/a/b/3.txt", "third");
            put("/ignore/4.txt", "4th");

            Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "working-directory-namespace-files");

            assertThat(execution.getTaskRunList()).hasSize(6);
            assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.WARNING);
            assertThat(execution.findTaskRunsByTaskId("t4").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
            assertThat(taskOutputService.getOutputs(execution.findTaskRunsByTaskId("t1").getFirst()).get("value")).isEqualTo("first");
            assertThat(taskOutputService.getOutputs(execution.findTaskRunsByTaskId("t2").getFirst()).get("value")).isEqualTo("second");
            assertThat(taskOutputService.getOutputs(execution.findTaskRunsByTaskId("t3").getFirst()).get("value")).isEqualTo("third");
        }

        public void namespaceFilesWithNamespaces(TestRunnerUtils runnerUtils)
            throws TimeoutException, IOException, QueueException, URISyntaxException, io.kestra.core.exceptions.InternalException {
            //fist namespace
            put("/test/a/b/c/1.txt", "first in first namespace", "io.test.first");
            put("/a/b/c/2.txt", "second in first namespace", "io.test.first");
            put("/a/b/3.txt", "third in first namespace", "io.test.first");
            put("/ignore/4.txt", "4th");

            //second namespace
            put("/test/a/b/c/1.txt", "first in second namespace", "io.test.second");
            put("/a/b/c/2.txt", "second in second namespace", "io.test.second");

            //third namespace
            put("/test/a/b/c/1.txt", "first in third namespace", "io.test.third");

            Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "working-directory-namespace-files-with-namespaces");

            assertThat(execution.getTaskRunList()).hasSize(6);
            assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.WARNING);
            assertThat(execution.findTaskRunsByTaskId("t4").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
            assertThat(taskOutputService.getOutputs(execution.findTaskRunsByTaskId("t1").getFirst()).get("value")).isEqualTo("first in third namespace");
            assertThat(taskOutputService.getOutputs(execution.findTaskRunsByTaskId("t2").getFirst()).get("value")).isEqualTo("second in second namespace");
            assertThat(taskOutputService.getOutputs(execution.findTaskRunsByTaskId("t3").getFirst()).get("value")).isEqualTo("third in first namespace");
        }

        @SuppressWarnings("unchecked")
        public void encryption(TestRunnerUtils runnerUtils, RunContextFactory runContextFactory)
            throws TimeoutException, GeneralSecurityException, QueueException, io.kestra.core.exceptions.InternalException {
            Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "working-directory-taskrun-encrypted");

            assertThat(execution.getTaskRunList()).hasSize(3);
            Map<String, Object> encryptedString = (Map<String, Object>) taskOutputService.getOutputs(execution.findTaskRunsByTaskId("encrypted").getFirst()).get("value");
            assertThat(encryptedString.get("type")).isEqualTo(EncryptedString.TYPE);
            String encryptedValue = (String) encryptedString.get("value");
            assertThat(encryptedValue).isNotEqualTo("Hello World");
            assertThat(runContextFactory.of().decrypt(encryptedValue)).isEqualTo("Hello World");
            assertThat(taskOutputService.getOutputs(execution.findTaskRunsByTaskId("decrypted").getFirst()).get("value")).isEqualTo("Hello World");
        }

        public void invalidWhen(TestRunnerUtils runnerUtils) throws TimeoutException, QueueException {
            Execution execution = runnerUtils.runOne(
                MAIN_TENANT, "io.kestra.tests", "working-directory-invalid-when", null,
                (f, e) -> ImmutableMap.of("failed", "false"), Duration.ofSeconds(60)
            );

            assertThat(execution.getTaskRunList()).hasSize(2);
            assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        }

        private void put(String path, String content) throws IOException, URISyntaxException {
            put(path, content, "io.kestra.tests");
        }

        private void put(String path, String content, String namespace) throws IOException, URISyntaxException {
            namespaceFactory.of(MAIN_TENANT, namespace, storageInterface).putFile(Path.of(path), new ByteArrayInputStream(content.getBytes()));
        }
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    @Plugin
    public static class LocalFiles extends Task implements RunnableTask<LocalFiles.LocalFilesOutput> {
        @Schema(
            title = "The files to be created on the local filesystem; it can be a map or a JSON object.",
            oneOf = { Map.class, String.class }
        )
        @PluginProperty(dynamic = true)
        private Object inputs;

        @Schema(
            title = "The files from the local filesystem to be sent to the Kestra's internal storage",
            description = "Must be a list of [glob](https://en.wikipedia.org/wiki/Glob_(programming)) expressions relative to the current working directory, some examples: `my-dir/**`, `my-dir/*/**` or `my-dir/my-file.txt`."
        )
        private Property<List<String>> outputs;

        @Override
        public LocalFiles.LocalFilesOutput run(RunContext runContext) throws Exception {
            FilesService.inputFiles(runContext, this.inputs);
            Map<String, URI> outputFiles = FilesService.outputFiles(runContext, runContext.render(this.outputs).asList(String.class));

            return LocalFiles.LocalFilesOutput.builder()
                .uris(outputFiles)
                .build();
        }

        @Builder
        @Getter
        public static class LocalFilesOutput implements Output {
            @Schema(title = "The URI of the files that have been sent to the Kestra's internal storage")
            private Map<String, URI> uris;
        }
    }
}
