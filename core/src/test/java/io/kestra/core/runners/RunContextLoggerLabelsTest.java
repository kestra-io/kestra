package io.kestra.core.runners;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.configuration.LoggingConfiguration;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@MicronautTest
@org.junit.jupiter.api.parallel.Execution(ExecutionMode.SAME_THREAD)
class RunContextLoggerLabelsTest {
    @Inject
    private LogEntryEmitter logEntryEmitter;

    @Test
    void shouldPropagateOnlyAllowListedLabelsToMdc() {
        // Given
        RunContextLoggerFactory factory = new RunContextLoggerFactory(logEntryEmitter, new LoggingConfiguration(List.of("team")));
        Execution execution = executionWithLabels(new Label("team", "payments"), new Label("env", "prod"));

        // When
        ILoggingEvent event = forwardAndCapture(factory.create(execution), "allow-listed-labels");

        // Then
        assertThat(event.getMDCPropertyMap()).containsEntry("team", "payments");
        assertThat(event.getMDCPropertyMap()).doesNotContainKey("env");
        assertThat(event.getMDCPropertyMap()).containsEntry("executionId", execution.getId());
    }

    @Test
    void shouldNotPropagateLabelsWhenNoneConfigured() {
        // Given
        RunContextLoggerFactory factory = new RunContextLoggerFactory(logEntryEmitter, new LoggingConfiguration(null));
        Execution execution = executionWithLabels(new Label("team", "payments"));

        // When
        ILoggingEvent event = forwardAndCapture(factory.create(execution), "no-labels-configured");

        // Then
        assertThat(event.getMDCPropertyMap()).doesNotContainKey("team");
        assertThat(event.getMDCPropertyMap()).containsEntry("executionId", execution.getId());
    }

    @Test
    void shouldPropagateAllowListedLabelsFromWorkerTaskVariables() {
        // Given a worker task carrying labels in its variables (no dedicated message field)
        RunContextLoggerFactory factory = new RunContextLoggerFactory(logEntryEmitter, new LoggingConfiguration(List.of("team")));
        WorkerTask workerTask = workerTaskWithLabels(new Label("team", "payments"), new Label("env", "prod"));

        // When
        ILoggingEvent event = forwardAndCapture(factory.create(workerTask), "worker-task-labels");

        // Then
        assertThat(event.getMDCPropertyMap()).containsEntry("team", "payments");
        assertThat(event.getMDCPropertyMap()).doesNotContainKey("env");
        assertThat(event.getMDCPropertyMap()).containsEntry("taskId", "task");
    }

    private Execution executionWithLabels(Label... labels) {
        Flow flow = TestsUtils.mockFlow();
        return TestsUtils.mockExecution(flow, Map.of()).withLabels(List.of(labels));
    }

    private WorkerTask workerTaskWithLabels(Label... labels) {
        TaskRun taskRun = TaskRun.builder()
            .id("tr-1")
            .executionId("exec-1")
            .namespace("io.kestra.tests")
            .flowId("flow")
            .taskId("task")
            .state(new State())
            .build();
        Task task = mock(Task.class);
        when(task.getLogLevel()).thenReturn(Level.TRACE);
        when(task.isLogToFile()).thenReturn(false);
        WorkerTaskData data = new WorkerTaskData(Map.of(RunVariables.LABELS, Label.toNestedMap(List.of(labels))), List.of(), null);
        return WorkerTask.builder().taskRun(taskRun).task(task).data(data).build();
    }

    private ILoggingEvent forwardAndCapture(RunContextLogger runContextLogger, String marker) {
        Logger flowLogger = (Logger) LoggerFactory.getLogger("flow");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        flowLogger.addAppender(appender);
        try {
            runContextLogger.logger().info(marker);
        } finally {
            flowLogger.detachAppender(appender);
        }

        return appender.list.stream()
            .filter(event -> marker.equals(event.getFormattedMessage()))
            .findFirst()
            .orElseThrow();
    }
}
