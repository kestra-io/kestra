package io.kestra.core.utils;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.TriggerContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

@Slf4j
class LogsTest {
    
    @Test
    void logFlow() {
        var flow = Flow.builder().namespace("namespace").id("flow").build();
        Logs.logExecution(flow, log, Level.INFO, "Some log");
        Logs.logExecution(flow, log, Level.INFO, "Some log with an {}", "attribute");
        Logs.logExecution(flow, log, Level.ERROR, "Some log with an {} and an error", "attribute", new RuntimeException("Test Exception"));
    }

    @Test
    void logExecution() {
        var execution = Execution.builder().namespace("namespace").flowId("flow").id("execution").build();
        Logs.logExecution(execution, log, Level.INFO, "Some log");
        Logs.logExecution(execution, log, Level.INFO, "Some log with an {}", "attribute");
        Logs.logExecution(execution, Level.INFO, "Some log");
    }

    @Test
    void logTrigger() {
        var trigger = TriggerContext.builder().namespace("namespace").flowId("flow").triggerId("trigger").build();
        Logs.logTrigger(trigger, log, Level.INFO, "Some log");
        Logs.logTrigger(trigger, log, Level.INFO, "Some log with an {}", "attribute");
        Logs.logTrigger(trigger, Level.INFO, "Some log");
    }

    @Test
    void logTaskRun() {
        var taskRun = TaskRun.builder().namespace("namespace").flowId("flow").executionId("execution").taskId("task").id("taskRun").build();
        Logs.logTaskRun(taskRun, Level.INFO, "Some log");
        Logs.logTaskRun(taskRun, Level.INFO, "Some log with an {}", "attribute");

        taskRun = TaskRun.builder().namespace("namespace").flowId("flow").executionId("execution").taskId("task").id("taskRun").value("value").build();
        Logs.logTaskRun(taskRun, Level.INFO, "Some log");
        Logs.logTaskRun(taskRun, Level.INFO, "Some log with an {}", "attribute");
    }
}