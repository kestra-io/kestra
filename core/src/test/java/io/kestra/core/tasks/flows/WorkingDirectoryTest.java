package io.kestra.core.tasks.flows;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.storages.InternalStorage;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WorkingDirectoryTest extends AbstractMemoryRunnerTest {
    @Inject
    Suite suite;

    @Test
    void success() throws TimeoutException {
       suite.success(runnerUtils);
    }

    @Test
    void failed() throws TimeoutException {
        suite.failed(runnerUtils);
    }

    @Test
    void each() throws TimeoutException {
        suite.each(runnerUtils);
    }

    @Test
    void cache() throws TimeoutException, IOException {
        suite.cache(runnerUtils);
    }

    @Test
    void taskrun() throws TimeoutException, InternalException {
        suite.taskRun(runnerUtils);
    }

    @Test
    void taskrunNested() throws TimeoutException, InternalException {
        suite.taskRunNested(runnerUtils);
    }

    @Test
    void namespaceFiles() throws TimeoutException, InternalException, IOException {
        suite.namespaceFiles(runnerUtils);
    }

    @Singleton
    public static class Suite {
        @Inject
        StorageInterface storageInterface;

        public void success(RunnerUtils runnerUtils) throws TimeoutException {
            Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "working-directory", null,
                (f, e) -> ImmutableMap.of("failed", "false"), Duration.ofSeconds(60)
            );

            assertThat(execution.getTaskRunList(), hasSize(4));
            assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
            assertThat((String) execution.getTaskRunList().get(3).getOutputs().get("value"), startsWith("kestra://"));
        }

        public void failed(RunnerUtils runnerUtils) throws TimeoutException {
            Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "working-directory", null,
                (f, e) -> ImmutableMap.of("failed", "true"), Duration.ofSeconds(60)
            );

            assertThat(execution.getTaskRunList(), hasSize(3));
            assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
            assertThat(execution.findTaskRunsByTaskId("error-t1"), hasSize(1));
        }

        public void each(RunnerUtils runnerUtils) throws TimeoutException {
            Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "working-directory-each", Duration.ofSeconds(60));

            assertThat(execution.getTaskRunList(), hasSize(8));
            assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
            assertThat((String) execution.findTaskRunsByTaskId("2_end").get(0).getOutputs().get("value"), startsWith("kestra://"));
        }

        @SuppressWarnings("unchecked")
        public void cache(RunnerUtils runnerUtils) throws TimeoutException, IOException {
            // make sure the cache didn't exist
            StorageContext storageContext = StorageContext.forFlow(Flow
                .builder()
                    .namespace("io.kestra.tests")
                    .id("working-directory-cache")
                .build()
            );
            InternalStorage storage = new InternalStorage(
                null,
                storageContext
                , storageInterface
            );
            storage.deleteCacheFile("workingDir", null);

            URI cacheURI = storageContext.getCacheURI("workingdir", null);
            assertFalse(storageInterface.exists(null, cacheURI));

            Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "working-directory-cache");

            assertThat(execution.getTaskRunList(), hasSize(3));
            assertThat(execution.getTaskRunList().stream()
                    .filter(t -> t.getTaskId().equals("exists"))
                    .findFirst().get()
                    .getOutputs(),
                nullValue()
            );
            assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
            assertTrue(storageInterface.exists(null, cacheURI));

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

        public void taskRun(RunnerUtils runnerUtils) throws TimeoutException, InternalException {
            Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "working-directory-taskrun");

            assertThat(execution.getTaskRunList(), hasSize(3));
            assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
            assertThat(((String) execution.findTaskRunByTaskIdAndValue("log-taskrun", List.of("1")).getOutputs().get("value")), containsString("1"));
        }

        public void taskRunNested(RunnerUtils runnerUtils) throws TimeoutException, InternalException {
            Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "working-directory-taskrun-nested");

            assertThat(execution.getTaskRunList(), hasSize(6));
            assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
            assertThat(((String) execution.findTaskRunByTaskIdAndValue("log-workerparent", List.of("1")).getOutputs().get("value")), containsString("{\"taskrun\":{\"value\":\"1\"}}"));
        }

        public void namespaceFiles(RunnerUtils runnerUtils) throws TimeoutException, InternalException, IOException {
            put("/test/a/b/c/1.txt", "first");
            put("/a/b/c/2.txt", "second");
            put("/a/b/3.txt", "third");
            put("/ignore/4.txt", "4th");

            Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "working-directory-namespace-files");

            assertThat(execution.getTaskRunList(), hasSize(6));
            assertThat(execution.getState().getCurrent(), is(State.Type.WARNING));
            assertThat(execution.findTaskRunsByTaskId("t4").get(0).getState().getCurrent(), is(State.Type.FAILED));
            assertThat(execution.findTaskRunsByTaskId("t1").get(0).getOutputs().get("value"), is("first"));
            assertThat(execution.findTaskRunsByTaskId("t2").get(0).getOutputs().get("value"), is("second"));
            assertThat(execution.findTaskRunsByTaskId("t3").get(0).getOutputs().get("value"), is("third"));
        }

        private void put(String path, String content) throws IOException {
            storageInterface.put(
                null,
                URI.create(StorageContext.namespaceFilePrefix("io.kestra.tests")  + path),
                new ByteArrayInputStream(content.getBytes())
            );
        }
    }
}
