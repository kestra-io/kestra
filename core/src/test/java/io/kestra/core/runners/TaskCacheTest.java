package io.kestra.core.runners;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.services.TaskOutputService;

import jakarta.inject.Inject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
public class TaskCacheTest {
    static final AtomicInteger COUNTER = new AtomicInteger(0);

    @Inject
    private TestRunnerUtils runnerUtils;

    @Inject
    private TaskOutputService taskOutputService;

    @BeforeEach
    void resetCounter() {
        COUNTER.set(0);
    }

    @Test
    @LoadFlows("flows/valids/cache.yaml")
    void shouldCacheTaskRunOutput() throws Exception {
        Execution execution = runnerUtils.runOne("main", "io.kestra.tests", "cache");
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(taskOutputService.getOutputs(execution.getTaskRunList().getFirst()).get("counter")).isEqualTo(1);

        // as the task is cached, it should return the same result
        Execution cached = runnerUtils.runOne("main", "io.kestra.tests", "cache");
        assertThat(cached.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(cached.getTaskRunList().size()).isEqualTo(1);
        assertThat(taskOutputService.getOutputs(cached.getTaskRunList().getFirst()).get("counter")).isEqualTo(1);
    }

    @Test
    @LoadFlows("flows/valids/cache.yaml")
    @Disabled("Expiration didn't work on CI for an unknown reason")
    void shouldExpireCacheTaskRunOutputAfterTtl() throws Exception {
        Execution execution = runnerUtils.runOne("main", "io.kestra.tests", "cache");
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(taskOutputService.getOutputs(execution.getTaskRunList().getFirst()).get("counter")).isEqualTo(1);

        // Wait for the cache TTL expiration
        Thread.sleep(1100);

        // as the task is cached, it should return the same result
        Execution notCached = runnerUtils.runOne("main", "io.kestra.tests", "cache");
        assertThat(notCached.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(notCached.getTaskRunList().size()).isEqualTo(1);
        assertThat(taskOutputService.getOutputs(notCached.getTaskRunList().getFirst()).get("counter")).isEqualTo(2);
    }

    @Test
    @LoadFlows("flows/valids/cache_with_file_and_purge.yaml")
    void shouldIgnoreTaskCacheEntryWhenReferencedFilesWerePurged() throws Exception {
        Execution firstExecution = runnerUtils.runOne("main", "io.kestra.tests", "cache_with_file_and_purge");
        assertThat(firstExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(taskOutputService.getOutputs(firstExecution.getTaskRunList().getFirst()).get("counter")).isEqualTo(1);
        assertThat(taskOutputService.getOutputs(firstExecution.getTaskRunList().get(1)).get("content")).isEqualTo("counter-1");

        Execution secondExecution = runnerUtils.runOne("main", "io.kestra.tests", "cache_with_file_and_purge");
        assertThat(secondExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(taskOutputService.getOutputs(secondExecution.getTaskRunList().getFirst()).get("counter")).isEqualTo(2);
        assertThat(taskOutputService.getOutputs(secondExecution.getTaskRunList().get(1)).get("content")).isEqualTo("counter-2");
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    @Plugin
    public static class CounterTask extends Task implements RunnableTask<CounterTask.Output> {

        private String workingDir;

        @Override
        public Output run(RunContext runContext) throws Exception {
            Map<String, Object> variables = Map.of("workingDir", runContext.workingDir().path().toString());
            runContext.render(this.workingDir, variables);
            return Output.builder()
                .counter(COUNTER.incrementAndGet())
                .build();
        }

        @SuperBuilder(toBuilder = true)
        @Getter
        public static class Output implements io.kestra.core.models.tasks.Output {
            private int counter;
        }

    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    @Plugin
    public static class FileProducerTask extends Task implements RunnableTask<FileProducerTask.Output> {
        @Override
        public Output run(RunContext runContext) throws Exception {
            int counter = COUNTER.incrementAndGet();
            URI uri = runContext.storage().putFile(
                runContext.workingDir().createFile("cached.txt", ("counter-" + counter).getBytes(StandardCharsets.UTF_8)).toFile()
            );

            return Output.builder()
                .counter(counter)
                .uri(uri)
                .build();
        }

        @SuperBuilder(toBuilder = true)
        @Getter
        public static class Output implements io.kestra.core.models.tasks.Output {
            private int counter;
            private URI uri;
        }
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    @Plugin
    public static class FileConsumerTask extends Task implements RunnableTask<FileConsumerTask.Output> {
        private String uri;

        @Override
        public Output run(RunContext runContext) throws Exception {
            URI renderedUri = URI.create(runContext.render(this.uri));
            String content;
            try (var inputStream = runContext.storage().getFile(renderedUri)) {
                content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }

            return Output.builder()
                .content(content)
                .build();
        }

        @SuperBuilder(toBuilder = true)
        @Getter
        public static class Output implements io.kestra.core.models.tasks.Output {
            private String content;
        }
    }
}
