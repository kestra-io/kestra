package io.kestra.executor.handler;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.event.Level;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.killswitch.KillSwitchService;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.ExecutionEvent;
import io.kestra.core.runners.ExecutionEventType;
import io.kestra.core.runners.LogEntryEmitter;
import io.kestra.core.utils.IdUtils;
import io.kestra.executor.ExecutorContext;
import io.kestra.plugin.core.flow.Loop;
import io.kestra.plugin.core.flow.Subflow;
import io.kestra.plugin.core.log.Log;

import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// The subflow creation log is emitted through LogEntryEmitter directly (bypassing the regular
// logging pipeline), so it must honor the parent task's configured logLevel explicitly — e.g.
// plugin defaults setting logLevel: WARN must suppress the INFO creation log (#16238).
@KestraTest
class ExecutionEventMessageHandlerSubflowLogTest {
    private static final String CREATION_LOG_PREFIX = "Created new execution";
    private static final int MAX_EXECUTOR_PASSES = 5;

    @Inject
    private ExecutionEventMessageHandler executionEventMessageHandler;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    KillSwitchService killSwitchService;

    @Inject
    LogEntryEmitter logEntryEmitter;

    @MockBean(KillSwitchService.class)
    KillSwitchService killSwitchService() {
        return mock(KillSwitchService.class);
    }

    @MockBean(LogEntryEmitter.class)
    LogEntryEmitter logEntryEmitter() {
        return mock(LogEntryEmitter.class);
    }

    @BeforeEach
    void setUp() {
        when(killSwitchService.evaluate(any(ExecutionEvent.class))).thenReturn(EvaluationType.PASS);
        when(logEntryEmitter.emits(any(LogEntry.class))).thenReturn(CompletableFuture.completedFuture(null));
        when(logEntryEmitter.emits(anyList())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void shouldEmitSubflowCreationLogByDefault() {
        // Given: a parent flow whose Subflow task has no configured logLevel
        var subflowExecutions = runParentFlowWithSubflowTask(null);

        // Then: the subflow execution was created and the INFO creation log was emitted
        assertThat(subflowExecutions).isTrue();
        assertThat(capturedCreationLogs()).isNotEmpty();
    }

    @Test
    void shouldNotEmitSubflowCreationLogWhenParentTaskLogLevelIsAboveInfo() {
        // Given: a parent flow whose Subflow task is configured with logLevel WARN
        var subflowExecutions = runParentFlowWithSubflowTask(Level.WARN);

        // Then: the subflow execution was created but the INFO creation log was suppressed
        assertThat(subflowExecutions).isTrue();
        assertThat(capturedCreationLogs()).isEmpty();
    }

    @Test
    void shouldTagSubflowCreationLogWithLoopKindWhenStartedInsideALoopIteration() {
        var childFlow = createChildFlow();
        var subflowTask = Subflow.builder()
            .id("subflow")
            .type(Subflow.class.getName())
            .namespace(childFlow.getNamespace())
            .flowId(childFlow.getId())
            .build();
        var parentFlow = flowRepository.create(GenericFlow.of(Flow.builder()
            .tenantId("tenant")
            .namespace("namespace")
            .id(IdUtils.create())
            .tasks(List.of(Loop.builder()
                .id("loop")
                .type(Loop.class.getName())
                .values(List.of("a"))
                .tasks(List.of(subflowTask))
                .build()))
            .build()));
        var rootExecution = Execution.newExecution(parentFlow, Collections.emptyList());
        executionRepository.save(rootExecution);

        var iteration = handleUntil(rootExecution, executor -> !executor.getLoopExecutions().isEmpty())
            .map(executor -> executor.getLoopExecutions().getFirst())
            .orElseThrow();
        executionRepository.save(iteration);
        var subflowStarted = handleUntil(iteration, executor -> !executor.getSubflowExecutions().isEmpty());

        assertThat(subflowStarted).isPresent();
        assertThat(capturedCreationLogs())
            .singleElement()
            .satisfies(logEntry -> {
                assertThat(logEntry.getExecutionId()).isEqualTo(iteration.getId());
                assertThat(logEntry.getExecutionKind()).isEqualTo(ExecutionKind.LOOP);
            });
    }

    /**
     * Creates a child flow and a parent flow holding a single Subflow task with the given logLevel,
     * then drives the handler until the subflow execution is produced.
     *
     * @return true when a subflow execution was produced within the allotted executor passes
     */
    private boolean runParentFlowWithSubflowTask(Level subflowTaskLogLevel) {
        var childFlow = createChildFlow();

        var parentFlow = flowRepository.create(GenericFlow.of(Flow.builder()
            .tenantId("tenant")
            .namespace("namespace")
            .id(IdUtils.create())
            .tasks(List.of(Subflow.builder()
                .id("subflow")
                .type(Subflow.class.getName())
                .namespace(childFlow.getNamespace())
                .flowId(childFlow.getId())
                .logLevel(subflowTaskLogLevel)
                .build()))
            .build()));

        var execution = Execution.newExecution(parentFlow, Collections.emptyList());
        executionRepository.save(execution);

        return handleUntil(execution, executor -> !executor.getSubflowExecutions().isEmpty()).isPresent();
    }

    private FlowWithSource createChildFlow() {
        return flowRepository.create(GenericFlow.of(Flow.builder()
            .tenantId("tenant")
            .namespace("namespace")
            .id(IdUtils.create())
            .tasks(List.of(Log.builder().id("log").type(Log.class.getName()).message("Hello from child").build()))
            .build()));
    }

    // The number of passes is an executor implementation detail, so re-handle until the condition holds.
    private Optional<ExecutorContext> handleUntil(Execution execution, Predicate<ExecutorContext> condition) {
        var maybeExecutor = executionEventMessageHandler.handle(new ExecutionEvent(execution, ExecutionEventType.CREATED));
        for (int pass = 0; pass < MAX_EXECUTOR_PASSES && maybeExecutor.isPresent(); pass++) {
            if (condition.test(maybeExecutor.get())) {
                return maybeExecutor;
            }
            var current = maybeExecutor.get().getExecution();
            executionRepository.save(current);
            maybeExecutor = executionEventMessageHandler.handle(new ExecutionEvent(current, ExecutionEventType.UPDATED));
        }
        return maybeExecutor.filter(condition);
    }

    private List<LogEntry> capturedCreationLogs() {
        ArgumentCaptor<LogEntry> captor = ArgumentCaptor.forClass(LogEntry.class);
        verify(logEntryEmitter, atLeast(0)).emits(captor.capture());
        return captor.getAllValues().stream()
            .filter(logEntry -> logEntry.getMessage() != null && logEntry.getMessage().startsWith(CREATION_LOG_PREFIX))
            .toList();
    }
}
