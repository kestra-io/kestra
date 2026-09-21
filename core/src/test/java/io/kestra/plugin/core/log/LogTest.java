package io.kestra.plugin.core.log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class LogTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    private DispatchQueueInterface<LogEntry> logQueue;

    @Test
    void shouldLogNonStringMessageListElementsInsteadOfCrashing() throws Exception {
        List<LogEntry> logs = new ArrayList<>();
        logQueue.addListener(logs::add);

        Log task = Log.builder()
            .id(IdUtils.create())
            .type(Log.class.getName())
            .message(List.of("hello", 42, true))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        task.run(runContext);

        List<LogEntry> matchingLogs = TestsUtils.awaitLogs(logs, 3);

        assertThat(matchingLogs.stream().filter(logEntry -> logEntry.getMessage().equals("hello")).count()).isEqualTo(1L);
        assertThat(matchingLogs.stream().filter(logEntry -> logEntry.getMessage().equals("42")).count()).isEqualTo(1L);
        assertThat(matchingLogs.stream().filter(logEntry -> logEntry.getMessage().equals("true")).count()).isEqualTo(1L);
    }
}
