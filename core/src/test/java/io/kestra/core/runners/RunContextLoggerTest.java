package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.tasks.log.Log;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@MicronautTest
class RunContextLoggerTest {
    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    QueueInterface<LogEntry> logQueue;

    @Inject
    RunContextFactory runContextFactory;


    @Test
    void logs() {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        List<LogEntry> matchingLog;
        logQueue.receive(either -> logs.add(either.getLeft()));

        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, Map.of());
        Log log = Log.builder().id(IdUtils.create()).type(Log.class.getName()).build();

        RunContextLogger runContextLogger = new RunContextLogger(
            logQueue,
            LogEntry.of(execution),
            Level.TRACE
        );

        Logger logger = runContextLogger.logger();
        logger.trace("trace");
        logger.debug("debug");
        logger.info("info");
        logger.warn("warn");
        logger.error("error");

        matchingLog = TestsUtils.awaitLogs(logs, 5);
        assertThat(matchingLog.stream().filter(logEntry -> logEntry.getLevel().equals(Level.TRACE)).findFirst().orElse(null).getMessage(), is("trace"));
        assertThat(matchingLog.stream().filter(logEntry -> logEntry.getLevel().equals(Level.DEBUG)).findFirst().orElse(null).getMessage(), is("debug"));
        assertThat(matchingLog.stream().filter(logEntry -> logEntry.getLevel().equals(Level.INFO)).findFirst().orElse(null).getMessage(), is("info"));
        assertThat(matchingLog.stream().filter(logEntry -> logEntry.getLevel().equals(Level.WARN)).findFirst().orElse(null).getMessage(), is("warn"));
        assertThat(matchingLog.stream().filter(logEntry -> logEntry.getLevel().equals(Level.ERROR)).findFirst().orElse(null).getMessage(), is("error"));
    }

    @Test
    void secrets() throws InterruptedException {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        List<LogEntry> matchingLog;
        logQueue.receive(either -> logs.add(either.getLeft()));

        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, Map.of());
        Log log = Log.builder().id(IdUtils.create()).type(Log.class.getName()).build();

        RunContextLogger runContextLogger = new RunContextLogger(
            logQueue,
            LogEntry.of(execution),
            Level.TRACE
        );

        runContextLogger.usedSecret("doe.com");
        runContextLogger.usedSecret("myawesomepass");

        Logger logger = runContextLogger.logger();
        // exception are not handle and secret will not be replaced
        logger.debug("test {} test", "john@doe.com", new Exception("exception from doe.com"));
        logger.info("test myawesomepassmyawesomepass myawesomepass myawesomepassmyawesomepass");

        matchingLog = TestsUtils.awaitLogs(logs, 3);
        assertThat(matchingLog.stream().filter(logEntry -> logEntry.getLevel().equals(Level.DEBUG)).findFirst().orElse(null).getMessage(), is("test john@******* test"));
        assertThat(matchingLog.stream().filter(logEntry -> logEntry.getLevel().equals(Level.TRACE)).findFirst().orElse(null).getMessage(), containsString("exception from doe.com"));
        assertThat(matchingLog.stream().filter(logEntry -> logEntry.getLevel().equals(Level.INFO)).findFirst().orElse(null).getMessage(), is("test **masked****************** ************* **************************"));
    }
}