package io.kestra.core.utils;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.TriggerContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
class LogsTest {

    private static final InMemoryAppender MEMORY_APPENDER = new InMemoryAppender();

    @BeforeAll
    static void setupLogger() {
        Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        MEMORY_APPENDER.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        MEMORY_APPENDER.start();
        logger.addAppender(MEMORY_APPENDER);
    }

    @AfterEach
    void clearLogs() {
        MEMORY_APPENDER.clear();
    }

    @Test
    void logFlow() {
        var flow = Flow.builder().tenantId("tenant").namespace("namespace").id("flow").build();
        Logs.logExecution(flow, log, Level.INFO, "Some log");
        Logs.logExecution(flow, log, Level.INFO, "Some log with an {}", "attribute");
        Logs.logExecution(flow, log, Level.ERROR, "Some log with an {} and an error", "attribute", new RuntimeException("Test Exception"));

        List<ILoggingEvent> logs = MEMORY_APPENDER.getLogs();
        assertThat(logs).hasSize(3);
    }

    @Test
    void logExecution() {
        var execution = Execution.builder().tenantId("tenant").namespace("namespace").flowId("flow").id("execution").build();
        Logs.logExecution(execution, Level.INFO, "Some log");
        Logs.logExecution(execution, Level.INFO, "Some log with an {}", "attribute");
        Logs.logExecution(execution, Level.INFO, "Some log");

        List<ILoggingEvent> logs = MEMORY_APPENDER.getLogs();
        assertThat(logs).hasSize(3);
        assertThat(logs.getFirst().getLoggerName()).isEqualTo("executor.tenant.namespace.flow");
    }

    @Test
    void logTrigger() {
        var trigger = TriggerContext.builder().tenantId("tenant").namespace("namespace").flowId("flow").triggerId("trigger").build();
        Logs.logTrigger(trigger, Level.INFO, "Some log");
        Logs.logTrigger(trigger, Level.INFO, "Some log with an {}", "attribute");
        Logs.logTrigger(trigger, Level.INFO, "Some log");

        List<ILoggingEvent> logs = MEMORY_APPENDER.getLogs();
        assertThat(logs).hasSize(3);
        assertThat(logs.getFirst().getLoggerName()).isEqualTo("scheduler.tenant.namespace.flow.trigger");
    }

    @Test
    void logTaskRun() {
        var taskRun = TaskRun.builder().tenantId("tenant").namespace("namespace").flowId("flow").executionId("execution").taskId("task").id("taskRun").build();
        Logs.logTaskRun(taskRun, Level.INFO, "Some log");
        Logs.logTaskRun(taskRun, Level.INFO, "Some log with an {}", "attribute");

        taskRun = TaskRun.builder().namespace("namespace").flowId("flow").executionId("execution").taskId("task").id("taskRun").value("value").build();
        Logs.logTaskRun(taskRun, Level.INFO, "Some log");
        Logs.logTaskRun(taskRun, Level.INFO, "Some log with an {}", "attribute");

        List<ILoggingEvent> logs = MEMORY_APPENDER.getLogs();
        assertThat(logs).hasSize(4);
        assertThat(logs.getFirst().getLoggerName()).isEqualTo("worker.tenant.namespace.flow.task");
    }

    private static class InMemoryAppender extends AppenderBase<ILoggingEvent> {
        private final List<ILoggingEvent> logs = new CopyOnWriteArrayList<>();

        @Override
        protected void append(ILoggingEvent event) {
            logs.add(event);
        }

        public List<ILoggingEvent> getLogs() {
            return logs;
        }

        public void clear() {
            logs.clear();
        }
    }
}