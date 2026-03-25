package io.kestra.core.runners;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@MicronautTest
@org.junit.jupiter.api.parallel.Execution(ExecutionMode.SAME_THREAD)
class RunContextLoggerTest {
    @Inject
    private DispatchQueueInterface<LogEntry> logQueue;

    @Inject
    private BroadcastQueueInterface<FollowLogEvent> followLogQueue;

    @Inject
    private LogEntryEmitter logEntryEmitter;

    @Test
    void logs() {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        logQueue.addListener(log -> logs.add(log));

        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, Map.of());

        RunContextLogger runContextLogger = new RunContextLogger(
            logEntryEmitter,
            LogEntry.of(execution),
            Level.TRACE,
            false
        );

        Logger logger = runContextLogger.logger();
        logger.trace("trace");
        logger.debug("debug");
        logger.info("info");
        logger.warn("warn");
        logger.error("error");

        List<LogEntry> matchingLog = TestsUtils.awaitLogs(logs, 5);
        assertThat(matchingLog.stream().filter(logEntry -> logEntry.getLevel().equals(Level.TRACE)).findFirst().orElseThrow().getMessage()).isEqualTo("trace");
        assertThat(matchingLog.stream().filter(logEntry -> logEntry.getLevel().equals(Level.DEBUG)).findFirst().orElseThrow().getMessage()).isEqualTo("debug");
        assertThat(matchingLog.stream().filter(logEntry -> logEntry.getLevel().equals(Level.INFO)).findFirst().orElseThrow().getMessage()).isEqualTo("info");
        assertThat(matchingLog.stream().filter(logEntry -> logEntry.getLevel().equals(Level.WARN)).findFirst().orElseThrow().getMessage()).isEqualTo("warn");
        assertThat(matchingLog.stream().filter(logEntry -> logEntry.getLevel().equals(Level.ERROR)).findFirst().orElseThrow().getMessage()).isEqualTo("error");
    }

    @Test
    void emptyLogMessage() {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        List<LogEntry> matchingLog;
        logQueue.addListener(log -> logs.add(log));

        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, Map.of());

        RunContextLogger runContextLogger = new RunContextLogger(
            logEntryEmitter,
            LogEntry.of(execution),
            Level.TRACE,
            false
        );

        Logger logger = runContextLogger.logger();
        logger.info("");

        matchingLog = TestsUtils.awaitLogs(logs, 1);
        assertThat(matchingLog.stream().findFirst().orElseThrow().getMessage()).isEmpty();
    }

    @Test
    void secrets() {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        List<LogEntry> matchingLog;
        logQueue.addListener(log -> logs.add(log));

        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, Map.of());

        RunContextLogger runContextLogger = new RunContextLogger(
            logEntryEmitter,
            LogEntry.of(execution),
            Level.TRACE,
            false
        );

        runContextLogger.usedSecret("doe.com");
        runContextLogger.usedSecret("myawesomepass");
        runContextLogger.usedSecret("http://it-s.secret");
        runContextLogger.usedSecret("");
        runContextLogger.usedSecret(null);

        Logger logger = runContextLogger.logger();
        // exception are not handle and secret will not be replaced
        logger.debug("test {} test", "john@doe.com", new Exception("exception from doe.com"));
        logger.info("test {} myawesomepassmyawesomepass myawesomepass myawesomepassmyawesomepass", Base64.getEncoder().encodeToString("myawesomepass".getBytes(StandardCharsets.UTF_8)));
        logger.warn("test {}", URI.create("http://it-s.secret"));

        // the 3 logs will create 4 log entries as exceptions stacktraces are logged separately at the TRACE level
        matchingLog = TestsUtils.awaitLogs(logs, 4);
        assertThat(matchingLog.stream().filter(logEntry -> logEntry.getLevel().equals(Level.DEBUG)).findFirst().orElseThrow().getMessage()).isEqualTo("test john@****** test");
        assertThat(matchingLog.stream().filter(logEntry -> logEntry.getLevel().equals(Level.TRACE)).findFirst().orElseThrow().getMessage()).contains("exception from doe.com");
        assertThat(matchingLog.stream().filter(logEntry -> logEntry.getLevel().equals(Level.INFO)).findFirst().orElseThrow().getMessage())
            .isEqualTo("test ****** ************ ****** ************");
        assertThat(matchingLog.stream().filter(logEntry -> logEntry.getLevel().equals(Level.WARN)).findFirst().orElseThrow().getMessage()).isEqualTo("test ******");
    }

    @Test
    void emitLog_sendsToBothQueues() {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        List<FollowLogEvent> followLogs = new CopyOnWriteArrayList<>();
        logQueue.addListener(logs::add);
        followLogQueue.addListener(followLogs::add);

        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, Map.of());

        RunContextLogger runContextLogger = new RunContextLogger(
            logEntryEmitter,
            LogEntry.of(execution),
            Level.TRACE,
            false
        );

        LogEntry base = LogEntry.of(execution).toBuilder()
            .timestamp(Instant.now())
            .level(Level.INFO)
            .thread(Thread.currentThread().getName())
            .message("hello emitLog")
            .build();

        runContextLogger.emitLog(base);

        List<LogEntry> queueLogs = TestsUtils.awaitLogs(logs, 1);
        List<FollowLogEvent> followQueueLogs = await().atMost(Duration.ofSeconds(10))
            .until(
                () -> followLogs,
                it -> it.size() == 1
            );

        assertThat(queueLogs).hasSize(1);
        assertThat(followQueueLogs).hasSize(1);
        assertThat(queueLogs.getFirst()).isEqualTo(base);
        assertThat(followQueueLogs.getFirst()).isEqualTo(FollowLogEvent.from(base));
    }

    @Test
    void emitLogs_sendsToBothQueues() {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        List<FollowLogEvent> followLogs = new CopyOnWriteArrayList<>();
        logQueue.addListener(logs::add);
        followLogQueue.addListener(followLogs::add);

        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, Map.of());

        RunContextLogger runContextLogger = new RunContextLogger(
            logEntryEmitter,
            LogEntry.of(execution),
            Level.TRACE,
            false
        );

        LogEntry e1 = LogEntry.of(execution).toBuilder()
            .timestamp(Instant.now())
            .level(Level.INFO)
            .thread(Thread.currentThread().getName())
            .message("first")
            .build();
        LogEntry e2 = LogEntry.of(execution).toBuilder()
            .timestamp(Instant.now())
            .level(Level.WARN)
            .thread(Thread.currentThread().getName())
            .message("second")
            .build();

        runContextLogger.emitLogs(List.of(e1, e2));

        List<LogEntry> queueLogs = TestsUtils.awaitLogs(logs, 2);
        List<FollowLogEvent> followQueueLogs = await().atMost(Duration.ofSeconds(10))
            .until(
                () -> followLogs,
                it -> it.size() == 2
            );

        assertThat(queueLogs).containsExactlyInAnyOrder(e1, e2);
        assertThat(followQueueLogs).containsExactlyInAnyOrder(FollowLogEvent.from(e1), FollowLogEvent.from(e2));
    }
}
