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
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
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

    @Test
    void emitDynamicTaskRunLogs_forcesContextAndAttemptZeroAndMasks() {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        logQueue.addListener(logs::add);

        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, Map.of());

        RunContextLogger runContextLogger = new RunContextLogger(
            logEntryEmitter,
            LogEntry.of(execution),
            Level.TRACE,
            false
        );
        runContextLogger.usedSecret("super-secret-value");

        // a dynamic taskrun that (deliberately) carries a foreign execution/tenant and one attempt:
        // the emitted entry must NOT inherit those — execution/tenant/namespace/flow come from the context.
        TaskRun dynamicTaskRun = TaskRun.builder()
            .id("dyn-taskrun-id")
            .taskId("Play | Task 1")
            .tenantId("other-tenant")
            .executionId("other-execution")
            .namespace("other.namespace")
            .flowId("other-flow")
            .attempts(List.of(TaskRunAttempt.builder().build()))
            .build();

        runContextLogger.emitDynamicTaskRunLogs(dynamicTaskRun, List.of(new DynamicTaskRunLog(Level.ERROR, "leak super-secret-value here")));

        List<LogEntry> queueLogs = TestsUtils.awaitLogs(logs, 1);
        assertThat(queueLogs).hasSize(1);
        LogEntry emitted = queueLogs.getFirst();
        // taskrun identity comes from the dynamic taskrun
        assertThat(emitted.getTaskRunId()).isEqualTo("dyn-taskrun-id");
        assertThat(emitted.getTaskId()).isEqualTo("Play | Task 1");
        // attempt is forced to 0 (these taskruns have one attempt; the log view groups by the 0-based attempt)
        assertThat(emitted.getAttemptNumber()).isEqualTo(0);
        // execution/tenant/namespace/flow are forced from the context, never from the (foreign) taskrun
        assertThat(emitted.getExecutionId()).isEqualTo(execution.getId());
        assertThat(emitted.getExecutionId()).isNotEqualTo("other-execution");
        assertThat(emitted.getTenantId()).isEqualTo(execution.getTenantId());
        assertThat(emitted.getNamespace()).isEqualTo(execution.getNamespace());
        assertThat(emitted.getFlowId()).isEqualTo(execution.getFlowId());
        // level preserved + secret masked
        assertThat(emitted.getLevel()).isEqualTo(Level.ERROR);
        assertThat(emitted.getMessage()).isEqualTo("leak ****** here");
    }

    @Test
    void emitDynamicTaskRunLogs_inheritsLevelFilter() {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        logQueue.addListener(logs::add);

        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, Map.of());

        // the context filters at WARN: an INFO dynamic line must be dropped, like any task log
        RunContextLogger runContextLogger = new RunContextLogger(
            logEntryEmitter,
            LogEntry.of(execution),
            Level.WARN,
            false
        );

        TaskRun dynamicTaskRun = TaskRun.builder()
            .id("dyn-taskrun-id")
            .taskId("Play | Task 1")
            .attempts(List.of(TaskRunAttempt.builder().build()))
            .build();

        runContextLogger.emitDynamicTaskRunLogs(dynamicTaskRun, List.of(
            new DynamicTaskRunLog(Level.INFO, "info dropped by filter"),
            new DynamicTaskRunLog(Level.ERROR, "error kept")
        ));

        List<LogEntry> queueLogs = TestsUtils.awaitLogs(logs, 1);
        assertThat(queueLogs).hasSize(1);
        assertThat(queueLogs.getFirst().getLevel()).isEqualTo(Level.ERROR);
        assertThat(queueLogs.getFirst().getMessage()).isEqualTo("error kept");
        assertThat(queueLogs).noneMatch(l -> l.getLevel().equals(Level.INFO));
    }

    @Test
    void emitDynamicTaskRunLogs_underLogToFileGoesToFileNotQueue() throws Exception {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        logQueue.addListener(logs::add);

        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, Map.of());

        // logToFile=true: task logs are file-only, so the dynamic lines must land in the file
        // (with masking) and never reach the inline log queue
        RunContextLogger runContextLogger = new RunContextLogger(
            logEntryEmitter,
            LogEntry.of(execution),
            Level.TRACE,
            true
        );
        runContextLogger.usedSecret("super-secret-value");

        TaskRun dynamicTaskRun = TaskRun.builder()
            .id("dyn-taskrun-id")
            .taskId("Play | Task 1")
            .attempts(List.of(TaskRunAttempt.builder().build()))
            .build();

        runContextLogger.emitDynamicTaskRunLogs(dynamicTaskRun, List.of(
            new DynamicTaskRunLog(Level.INFO, "to file super-secret-value")
        ));

        runContextLogger.closeLogFile();
        String fileContent = java.nio.file.Files.readString(runContextLogger.getLogFile().toPath());
        assertThat(fileContent).contains("to file ******");
        // file-only: ContextAppender is not attached, so nothing reaches the inline queue
        assertThat(logs).isEmpty();
    }

    @Test
    void emitDynamicTaskRunLogs_seedsMDCWithDynamicTaskRunIdentity() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, Map.of());

        // mirror exactly how emitDynamicTaskRunLogs binds the child logger for a dynamic taskrun:
        // execution context + the dynamic taskrun's id/taskId, attempt 0
        LogEntry boundLogEntry = LogEntry.of(execution).toBuilder()
            .taskId("Play | Task 1")
            .taskRunId("dyn-taskrun-id")
            .attemptNumber(0)
            .build();

        RunContextLogger runContextLogger = new RunContextLogger(
            logEntryEmitter,
            boundLogEntry,
            Level.TRACE,
            false
        );
        ch.qos.logback.classic.Logger perRunLogger =
            (ch.qos.logback.classic.Logger) runContextLogger.logger();

        // the per-run MDC carries the dynamic taskrun identity (taskRunId/taskId), not just the
        // execution context — so forwarded server logs are attributed to the dynamic taskrun too
        assertThat(perRunLogger.getLoggerContext().getMDCAdapter().getCopyOfContextMap())
            .containsEntry("taskRunId", "dyn-taskrun-id")
            .containsEntry("taskId", "Play | Task 1")
            .containsEntry("executionId", execution.getId())
            .containsEntry("namespace", execution.getNamespace())
            .containsEntry("flowId", execution.getFlowId());
    }

    @Test
    void transformPreservesMDC() throws Exception {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, Map.of());
        LogEntry logEntry = LogEntry.of(execution);

        RunContextLogger runContextLogger = new RunContextLogger(
            logEntryEmitter,
            logEntry,
            Level.TRACE,
            false
        );
        // initializeLogger() populates the per-run LoggerContext's MDC adapter on this thread.
        ch.qos.logback.classic.Logger perRunLogger =
            (ch.qos.logback.classic.Logger) runContextLogger.logger();

        LoggingEvent original = new LoggingEvent(
            RunContextLoggerTest.class.getName(),
            perRunLogger,
            ch.qos.logback.classic.Level.INFO,
            "msg",
            null,
            null
        );
        ILoggingEvent transformed = new TransformExposingAppender(runContextLogger, perRunLogger)
            .transform(original);

        // Clear the per-run MDC adapter so the lazy lookup in getMDCPropertyMap() would
        // hit an empty map. The only remaining path to non-empty MDC is the eager snapshot
        // set by lle.setMDCPropertyMap(...) inside transform(). Removing that call makes
        // this assertion fail.
        perRunLogger.getLoggerContext().getMDCAdapter().clear();

        assertThat(transformed.getMDCPropertyMap())
            .containsEntry("tenantId", logEntry.getTenantId())
            .containsEntry("namespace", logEntry.getNamespace())
            .containsEntry("flowId", logEntry.getFlowId())
            .containsEntry("executionId", logEntry.getExecutionId());
    }

    @Test
    void resetMDCClearsThePerRunAdapter() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, Map.of());
        LogEntry logEntry = LogEntry.of(execution);

        RunContextLogger runContextLogger = new RunContextLogger(
            logEntryEmitter,
            logEntry,
            Level.TRACE,
            false
        );
        ch.qos.logback.classic.Logger perRunLogger =
            (ch.qos.logback.classic.Logger) runContextLogger.logger();
        var adapter = perRunLogger.getLoggerContext().getMDCAdapter();

        assertThat(adapter.getCopyOfContextMap())
            .containsEntry("tenantId", logEntry.getTenantId())
            .containsEntry("namespace", logEntry.getNamespace())
            .containsEntry("flowId", logEntry.getFlowId())
            .containsEntry("executionId", logEntry.getExecutionId());

        runContextLogger.resetMDC();

        assertThat(adapter.getCopyOfContextMap()).isNullOrEmpty();
    }

    /**
     * Exposes the protected {@link RunContextLogger.BaseAppender#transform} for the test.
     */
    private static final class TransformExposingAppender extends RunContextLogger.BaseAppender {
        TransformExposingAppender(RunContextLogger runContextLogger, ch.qos.logback.classic.Logger logger) {
            super(runContextLogger, logger);
        }

        @Override
        protected void append(ILoggingEvent event) {
            // unused
        }
    }
}
