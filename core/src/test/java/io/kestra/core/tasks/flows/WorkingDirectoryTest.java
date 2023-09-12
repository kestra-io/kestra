package io.kestra.core.tasks.flows;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.storages.StorageInterface;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
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

        public void cache(RunnerUtils runnerUtils) throws TimeoutException, IOException {
            // make sure the cache didn't exist
            URI cache = URI.create(storageInterface.cachePrefix("io.kestra.tests", "working-directory-cache", "workingDir", null) + "/cache.zip");
            storageInterface.delete(null, cache);

            Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "working-directory-cache");

            assertThat(execution.getTaskRunList(), hasSize(2));
            assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
            assertTrue(storageInterface.exists(null, cache));

            // a second run should use the cache so the execution failed as the localfile cannot create the file as it already exist
            execution = runnerUtils.runOne(null, "io.kestra.tests", "working-directory-cache");

            assertThat(execution.getTaskRunList(), hasSize(2));
            assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        }
    }
}
