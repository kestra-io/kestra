package io.kestra.core.models.tasks.runners.types;

import io.kestra.core.models.tasks.runners.AbstractTaskRunnerTest;
import io.kestra.core.models.tasks.runners.TaskRunner;
import io.kestra.plugin.core.runner.Process;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.runners.ScriptService;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ProcessTest extends AbstractTaskRunnerTest {

    @Override
    protected TaskRunner<?> taskRunner() {
        return new Process();
    }

    @Test
void readersRunOnPlatformThreads() throws Exception {
    var runContext = runContext(this.runContextFactory);
    var commands = initScriptCommands(runContext);

    Mockito.when(commands.getCommands()).thenReturn(
        Property.ofValue(
            ScriptService.scriptCommands(
                List.of("/bin/sh", "-c"),
                Collections.emptyList(),
                List.of("sleep 5")
            )
        )
    );

    var execution = CompletableFuture.runAsync(() -> {
        try {
            taskRunner().run(
                runContext, commands, Collections.emptyList()
            );
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    });

    try {
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            var threads = Thread.getAllStackTraces().keySet();

            assertThat(threads).anyMatch(thread ->
                thread.getName().equals("process-log-out")
                    && thread.isDaemon()
                    && !thread.isVirtual()
            );

            assertThat(threads).anyMatch(thread ->
                thread.getName().equals("process-log-err")
                    && thread.isDaemon()
                    && !thread.isVirtual()
            );
        });
    } finally {
        execution.get(10, TimeUnit.SECONDS);
    }
}
}
