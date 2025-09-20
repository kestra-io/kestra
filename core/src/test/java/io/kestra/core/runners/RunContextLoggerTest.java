package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.utils.TestsUtils;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.Logger;
import org.slf4j.event.Level;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

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
        assertThat(matchingLog.stream().filter(logEntry -> logEntry.getLevel().equals(Level.INFO)).findFirst().orElseThrow().getMessage()).isEqualTo("test ****** ************ ****** ************");
        assertThat(matchingLog.stream().filter(logEntry -> logEntry.getLevel().equals(Level.WARN)).findFirst().orElseThrow().getMessage()).isEqualTo("test ******");
    }
}
