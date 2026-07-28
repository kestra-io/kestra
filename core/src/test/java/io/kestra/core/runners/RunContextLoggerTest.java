package io.kestra.core.runners;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.utils.TestsUtils;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import reactor.core.publisher.Flux;
import org.slf4j.event.KeyValuePair;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
@org.junit.jupiter.api.parallel.Execution(ExecutionMode.SAME_THREAD)
class RunContextLoggerTest {
    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    QueueInterface<LogEntry> logQueue;

    @Test
    void logs() {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, either -> logs.add(either.getLeft()));

        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, Map.of());

        RunContextLogger runContextLogger = new RunContextLogger(
            logQueue,
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
        receive.blockLast();
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
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, either -> logs.add(either.getLeft()));

        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, Map.of());

        RunContextLogger runContextLogger = new RunContextLogger(
            logQueue,
            LogEntry.of(execution),
            Level.TRACE,
            false
        );

        Logger logger = runContextLogger.logger();
        logger.info("");

        matchingLog = TestsUtils.awaitLogs(logs, 1);
        receive.blockLast();
        assertThat(matchingLog.stream().findFirst().orElseThrow().getMessage()).isEmpty();
    }

    @Test
    void secrets() {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        List<LogEntry> matchingLog;
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, either -> logs.add(either.getLeft()));

        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, Map.of());

        RunContextLogger runContextLogger = new RunContextLogger(
            logQueue,
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
        receive.blockLast();
        assertThat(matchingLog.stream().filter(logEntry -> logEntry.getLevel().equals(Level.DEBUG)).findFirst().orElseThrow().getMessage()).isEqualTo("test john@****** test");
        assertThat(matchingLog.stream().filter(logEntry -> logEntry.getLevel().equals(Level.TRACE)).findFirst().orElseThrow().getMessage()).contains("exception from doe.com");
        assertThat(matchingLog.stream().filter(logEntry -> logEntry.getLevel().equals(Level.INFO)).findFirst().orElseThrow().getMessage())
            .isEqualTo("test ****** ************ ****** ************");
        assertThat(matchingLog.stream().filter(logEntry -> logEntry.getLevel().equals(Level.WARN)).findFirst().orElseThrow().getMessage()).isEqualTo("test ******");
    }

    @Test
    void transformPreservesMDC() throws Exception {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, Map.of());
        LogEntry logEntry = LogEntry.of(execution);

        RunContextLogger runContextLogger = new RunContextLogger(
            logQueue,
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
            logQueue,
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

    @Test
    void emitDynamicTaskRunLogs_forcesContextAndAttemptZeroAndMasks() {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, either -> logs.add(either.getLeft()));

        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, Map.of());

        RunContextLogger runContextLogger = new RunContextLogger(
            logQueue,
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
        receive.blockLast();
        assertThat(queueLogs).hasSize(1);
        LogEntry emitted = queueLogs.getFirst();
        assertThat(emitted.getTaskRunId()).isEqualTo("dyn-taskrun-id");
        assertThat(emitted.getTaskId()).isEqualTo("Play | Task 1");
        assertThat(emitted.getAttemptNumber()).isEqualTo(0);
        assertThat(emitted.getExecutionId()).isEqualTo(execution.getId());
        assertThat(emitted.getExecutionId()).isNotEqualTo("other-execution");
        assertThat(emitted.getTenantId()).isEqualTo(execution.getTenantId());
        assertThat(emitted.getNamespace()).isEqualTo(execution.getNamespace());
        assertThat(emitted.getFlowId()).isEqualTo(execution.getFlowId());
        assertThat(emitted.getLevel()).isEqualTo(Level.ERROR);
        assertThat(emitted.getMessage()).isEqualTo("leak ****** here");
    }

    @Test
    void emitDynamicTaskRunLogs_inheritsLevelFilter() {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, either -> logs.add(either.getLeft()));

        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, Map.of());

        // the context filters at WARN: an INFO dynamic line must be dropped, like any task log
        RunContextLogger runContextLogger = new RunContextLogger(
            logQueue,
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
        receive.blockLast();
        assertThat(queueLogs).hasSize(1);
        assertThat(queueLogs.getFirst().getLevel()).isEqualTo(Level.ERROR);
        assertThat(queueLogs.getFirst().getMessage()).isEqualTo("error kept");
        assertThat(queueLogs).noneMatch(l -> l.getLevel().equals(Level.INFO));
    }

    @Test
    void emitDynamicTaskRunLogs_underLogToFileGoesToFileNotQueue() throws Exception {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, either -> logs.add(either.getLeft()));

        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, Map.of());

        // logToFile=true: task logs are file-only, so the dynamic lines must land in the file
        // (with masking) and never reach the inline log queue
        RunContextLogger runContextLogger = new RunContextLogger(
            logQueue,
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
        receive.blockLast();
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
            logQueue,
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
    void emitProgress_setsTypedProgressOnLogEntry() {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, either -> logs.add(either.getLeft()));

        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, Map.of());

        RunContextLogger runContextLogger = new RunContextLogger(
            logQueue,
            LogEntry.of(execution),
            Level.TRACE,
            false
        );

        // mirrors RunContext#emitProgress, which is a thin wrapper over this exact call
        runContextLogger.logger().atInfo().addKeyValue(RunContextLogger.PROGRESS_KEY, "pod.created").log("Pod created");
        runContextLogger.logger().info("a plain log line has no progress");

        List<LogEntry> matchingLog = TestsUtils.awaitLogs(logs, 2);
        receive.blockLast();
        LogEntry progressEntry = matchingLog.stream().filter(l -> "Pod created".equals(l.getMessage())).findFirst().orElseThrow();
        LogEntry plainEntry = matchingLog.stream().filter(l -> l.getMessage().startsWith("a plain log line")).findFirst().orElseThrow();

        assertThat(progressEntry.getProgress()).isEqualTo("pod.created");
        assertThat(plainEntry.getProgress()).isNull();
    }

    @Test
    void transformPreservesKeyValuePairs() throws Exception {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, Map.of());
        LogEntry logEntry = LogEntry.of(execution);

        RunContextLogger runContextLogger = new RunContextLogger(
            logQueue,
            logEntry,
            Level.TRACE,
            false
        );
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
        original.addKeyValuePair(new KeyValuePair(RunContextLogger.PROGRESS_KEY, "pod.created"));

        // transform() rebuilds the event from scratch; without re-attaching key-value pairs
        // the progress token would be silently lost before logEntry() ever gets to read it
        ILoggingEvent transformed = new TransformExposingAppender(runContextLogger, perRunLogger)
            .transform(original);

        assertThat(transformed.getKeyValuePairs())
            .extracting(kv -> kv.key, kv -> kv.value)
            .contains(org.assertj.core.groups.Tuple.tuple(RunContextLogger.PROGRESS_KEY, "pod.created"));
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
