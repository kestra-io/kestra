package io.kestra.plugin.core.flow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.common.EncryptedString;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.storages.InternalStorage;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.RetryingTest;

@KestraTest(startRunner = true)
public class WorkingDirectoryTest {
    @Inject
    Suite suite;

    @Inject
    RunContextFactory runContextFactory;

    @Inject
    RunnerUtils runnerUtils;

    @Test
    @LoadFlows({"flows/valids/working-directory.yaml"})
    void success() throws TimeoutException, QueueException {
       suite.success(runnerUtils);
    }

    @Test
    @LoadFlows({"flows/valids/working-directory.yaml"})
    void failed() throws TimeoutException, QueueException {
        suite.failed(runnerUtils);
    }

    @Test
    @LoadFlows({"flows/valids/working-directory-each.yaml"})
    void each() throws TimeoutException, QueueException {
        suite.each(runnerUtils);
    }

    @Test
    @LoadFlows({"flows/valids/working-directory-cache.yml"})
    void cache() throws TimeoutException, IOException, QueueException {
        suite.cache(runnerUtils);
    }

    @Test
    @LoadFlows({"flows/valids/working-directory-taskrun.yml"})
    void taskrun() throws TimeoutException, InternalException, QueueException {
        suite.taskRun(runnerUtils);
    }

    @Test
    @LoadFlows({"flows/valids/working-directory-taskrun-nested.yml"})
    void taskrunNested() throws TimeoutException, InternalException, QueueException {
        suite.taskRunNested(runnerUtils);
    }

    @RetryingTest(5)
    @LoadFlows({"flows/valids/working-directory-namespace-files.yaml"})
    void namespaceFiles() throws TimeoutException, IOException, QueueException {
        suite.namespaceFiles(runnerUtils);
    }

    @Test
    @LoadFlows({"flows/valids/working-directory-inputs.yml"})
    void inputFiles() throws Exception {
        suite.inputFiles(runnerUtils);
    }

    @Test
    @LoadFlows({"flows/valids/working-directory-outputs.yml"})
    void outputFiles() throws Exception {
        suite.outputFiles(runnerUtils);
    }

    @Test
    @LoadFlows({"flows/valids/working-directory-taskrun-encrypted.yml"})
    void encryption() throws Exception {
        suite.encryption(runnerUtils, runContextFactory);
    }

    @Singleton
    public static class Suite {
        @Inject
        StorageInterface storageInterface;

        public void success(RunnerUtils runnerUtils) throws TimeoutException, QueueException {
            Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "working-directory", null,
                (f, e) -> ImmutableMap.of("failed", "false"), Duration.ofSeconds(60)
            );

            assertThat(execution.getTaskRunList(), hasSize(4));
            assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
            assertThat((String) execution.getTaskRunList().get(3).getOutputs().get("value"), startsWith("kestra://"));
        }

        public void failed(RunnerUtils runnerUtils) throws TimeoutException, QueueException {
            Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "working-directory", null,
                (f, e) -> ImmutableMap.of("failed", "true"), Duration.ofSeconds(60)
            );

            assertThat(execution.getTaskRunList(), hasSize(3));
            assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
            assertThat(execution.findTaskRunsByTaskId("error-t1"), hasSize(1));
        }

        public void each(RunnerUtils runnerUtils) throws TimeoutException, QueueException {
            Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "working-directory-each", Duration.ofSeconds(60));

            assertThat(execution.getTaskRunList(), hasSize(8));
            assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
            assertThat((String) execution.findTaskRunsByTaskId("2_end").getFirst().getOutputs().get("value"), startsWith("kestra://"));
        }

        @SuppressWarnings("unchecked")
        public void outputFiles(RunnerUtils runnerUtils) throws TimeoutException, IOException, QueueException {

            Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "working-directory-outputs");

            assertThat(execution.getTaskRunList(), hasSize(2));
            assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

            TaskRun taskRun = execution.getTaskRunList().getFirst();
            Map<String, Object> outputs = taskRun.getOutputs();
            assertThat(outputs, hasKey("outputFiles"));

            StorageContext storageContext = StorageContext.forTask(taskRun);
            InternalStorage storage = new InternalStorage(
                null,
                storageContext,
                storageInterface,
                null
            );

            URI uri = ((Map<String, String>) outputs.get("outputFiles")).values()
                .stream()
                .map(URI::create)
                .toList().getFirst();
            assertThat(new String(storage.getFile(uri).readAllBytes()), is("Hello World"));
        }

        @SuppressWarnings("unchecked")
        public void inputFiles(RunnerUtils runnerUtils) throws TimeoutException, IOException, QueueException {

            Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "working-directory-inputs");

            assertThat(execution.getTaskRunList(), hasSize(2));
            assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

            StorageContext storageContext = StorageContext.forTask(execution.getTaskRunList().get(1));
            InternalStorage storage = new InternalStorage(
                null,
                storageContext,
                storageInterface,
                null
            );

            TaskRun taskRun = execution.getTaskRunList().get(1);
            Map<String, Object> outputs = taskRun.getOutputs();
            assertThat(outputs, hasKey("uris"));

            URI uri = URI.create(((Map<String, String>) outputs.get("uris")).get("input.txt"));

            assertTrue(uri.toString().endsWith("input.txt"));
            assertThat(new String(storage.getFile(uri).readAllBytes()), is("Hello World"));
        }

        @SuppressWarnings({"unchecked", "OptionalGetWithoutIsPresent"})
        public void cache(RunnerUtils runnerUtils) throws TimeoutException, IOException, QueueException {
            // make sure the cache didn't exist
            StorageContext storageContext = StorageContext.forFlow(Flow
                .builder()
                    .namespace("io.kestra.tests")
                    .id("working-directory-cache")
                .build()
            );
            InternalStorage storage = new InternalStorage(
                null,
                storageContext,
                storageInterface,
                null
            );
            storage.deleteCacheFile("workingDir", null);

            URI cacheURI = storageContext.getCacheURI("workingdir", null);
            assertFalse(storageInterface.exists(null, null, cacheURI));

            Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "working-directory-cache");

            assertThat(execution.getTaskRunList(), hasSize(3));
            assertThat(execution.getTaskRunList().stream()
                    .filter(t -> t.getTaskId().equals("exists"))
                    .findFirst().get()
                    .getOutputs(),
                is(Map.of("uris", Collections.emptyMap()))
            );
            assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
            assertTrue(storageInterface.exists(null, null, cacheURI));

            // a second run should use the cache so the task `exists` should output the cached file
            execution = runnerUtils.runOne(null, "io.kestra.tests", "working-directory-cache");

            assertThat(execution.getTaskRunList(), hasSize(3));
            assertThat(((Map<String, String>) execution.getTaskRunList().stream()
                    .filter(t -> t.getTaskId().equals("exists"))
                    .findFirst().get()
                    .getOutputs()
                    .get("uris"))
                    .containsKey("hello.txt"),
                is(true)
            );
            assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        }

        public void taskRun(RunnerUtils runnerUtils) throws TimeoutException, InternalException, QueueException {
            Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "working-directory-taskrun");

            assertThat(execution.getTaskRunList(), hasSize(3));
            assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
            assertThat(((String) execution.findTaskRunByTaskIdAndValue("log-taskrun", List.of("1")).getOutputs().get("value")), containsString("1"));
        }

        public void taskRunNested(RunnerUtils runnerUtils) throws TimeoutException, InternalException, QueueException {
            Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "working-directory-taskrun-nested");

            assertThat(execution.getTaskRunList(), hasSize(6));
            assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
            assertThat(((String) execution.findTaskRunByTaskIdAndValue("log-workerparent", List.of("1")).getOutputs().get("value")), containsString("{\"taskrun\":{\"value\":\"1\"}}"));
        }

        public void namespaceFiles(RunnerUtils runnerUtils) throws TimeoutException, IOException, QueueException {
            put("/test/a/b/c/1.txt", "first");
            put("/a/b/c/2.txt", "second");
            put("/a/b/3.txt", "third");
            put("/ignore/4.txt", "4th");

            Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "working-directory-namespace-files");

            assertThat(execution.getTaskRunList(), hasSize(6));
            assertThat(execution.getState().getCurrent(), is(State.Type.WARNING));
            assertThat(execution.findTaskRunsByTaskId("t4").getFirst().getState().getCurrent(), is(State.Type.FAILED));
            assertThat(execution.findTaskRunsByTaskId("t1").getFirst().getOutputs().get("value"), is("first"));
            assertThat(execution.findTaskRunsByTaskId("t2").getFirst().getOutputs().get("value"), is("second"));
            assertThat(execution.findTaskRunsByTaskId("t3").getFirst().getOutputs().get("value"), is("third"));
        }

        @SuppressWarnings("unchecked")
        public void encryption(RunnerUtils runnerUtils, RunContextFactory runContextFactory) throws TimeoutException, GeneralSecurityException, QueueException {
            Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "working-directory-taskrun-encrypted");

            assertThat(execution.getTaskRunList(), hasSize(3));
            Map<String, Object> encryptedString = (Map<String, Object>) execution.findTaskRunsByTaskId("encrypted").getFirst().getOutputs().get("value");
            assertThat(encryptedString.get("type"), is(EncryptedString.TYPE));
            String encryptedValue = (String) encryptedString.get("value");
            assertThat(encryptedValue, is(not("Hello World")));
            assertThat(runContextFactory.of().decrypt(encryptedValue), is("Hello World"));
            assertThat(execution.findTaskRunsByTaskId("decrypted").getFirst().getOutputs().get("value"), is("Hello World"));
        }

        private void put(String path, String content) throws IOException {
            storageInterface.put(
                null,
                null,
                URI.create(StorageContext.namespaceFilePrefix("io.kestra.tests")  + path),
                new ByteArrayInputStream(content.getBytes())
            );
        }
    }
}
