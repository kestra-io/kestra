package io.kestra.core.services;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.TriggerContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

@MicronautTest
@Slf4j
class LogServiceTest {
    @Inject
    private LogService logService;

    @Test
    void logFlow() {
        var flow = Flow.builder().namespace("namespace").id("flow").build();
        logService.logFlow(flow, log, Level.INFO, "Some log");
        logService.logFlow(flow, log, Level.INFO, "Some log with an {}", "attribute");
        logService.logFlow(flow, log, Level.ERROR, "Some log with an {} and an error", "attribute", new RuntimeException("Test Exception"));
    }

    @Test
    void logExecution() {
        var execution = Execution.builder().namespace("namespace").flowId("flow").id("execution").build();
        logService.logExecution(execution, log, Level.INFO, "Some log");
        logService.logExecution(execution, log, Level.INFO, "Some log with an {}", "attribute");
    }

    @Test
    void logTrigger() {
        var trigger = TriggerContext.builder().namespace("namespace").flowId("flow").triggerId("trigger").build();
        logService.logTrigger(trigger, log, Level.INFO, "Some log");
        logService.logTrigger(trigger, log, Level.INFO, "Some log with an {}", "attribute");
    }

    @Test
    void logTaskRun() {
        var taskRun = TaskRun.builder().namespace("namespace").flowId("flow").executionId("execution").taskId("task").id("taskRun").build();
        logService.logTaskRun(taskRun, log, Level.INFO, "Some log");
        logService.logTaskRun(taskRun, log, Level.INFO, "Some log with an {}", "attribute");

        taskRun = TaskRun.builder().namespace("namespace").flowId("flow").executionId("execution").taskId("task").id("taskRun").value("value").build();
        logService.logTaskRun(taskRun, log, Level.INFO, "Some log");
        logService.logTaskRun(taskRun, log, Level.INFO, "Some log with an {}", "attribute");
    }
}