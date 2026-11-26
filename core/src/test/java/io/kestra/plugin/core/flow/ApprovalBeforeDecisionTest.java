package io.kestra.plugin.core.flow;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.ApprovalRequestHandler;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.utils.MapUtils;

import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@KestraTest(startRunner = true)
class ApprovalBeforeDecisionTest {

    @Inject
    TestRunnerUtils runnerUtils;
    @Inject
    ExecutionService executionService;
    @Inject
    FlowRepositoryInterface flowRepository;
    @Inject
    ApprovalRequestHandler approvalRequestHandler;

    @MockBean(ApprovalRequestHandler.class)
    ApprovalRequestHandler approvalRequestHandler() throws Exception {
        ApprovalRequestHandler handler = spy(new ApprovalRequestHandler.NoopApprovalRequestHandler());
        doAnswer(invocation -> {
            Execution execution = invocation.getArgument(1);
            if (Boolean.TRUE.equals(MapUtils.emptyOnNull(execution.getInputs()).get("failOpen"))) {
                throw new IllegalArgumentException("The assignment names a user that does not exist.");
            }
            if (execution.findTaskRunsByTaskId("approval_prepare").stream().noneMatch(taskRun -> taskRun.getState().getCurrent() == State.Type.SUCCESS)) {
                throw new IllegalStateException("The request was opened before approval_prepare succeeded.");
            }
            return invocation.callRealMethod();
        }).when(handler).open(any(), any(), any(), any(), any());
        return handler;
    }

    @Test
    @LoadFlows({ "flows/valids/approval-before-decision.yaml" })
    void shouldOpenTheRequestOnceAfterTheTasksBeforeTheDecisionSucceeded() throws Exception {
        Execution paused = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "approval-before-decision", null, null, Duration.ofSeconds(30));

        assertThat(paused.getState().getCurrent()).isEqualTo(State.Type.PAUSED);
        assertThat(paused.findTaskRunsByTaskId("approved")).isEmpty();

        Execution decided = executionService.decide(
            paused,
            flowRepository.findByExecution(paused),
            paused.findTaskRunsByTaskId("approval").getFirst().getId(),
            new PausableTask.Decision(PausableTask.Decision.Type.APPROVED, null),
            Map.of()
        );
        Execution done = runnerUtils.emitAndAwaitExecution(e -> e.getId().equals(paused.getId()) && e.getState().isTerminated(), decided);

        assertThat(done.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(done.findTaskRunsByTaskId("approved")).hasSize(1);
        verify(approvalRequestHandler, times(1)).open(any(), argThat(e -> e.getId().equals(paused.getId())), any(), any(), any());
    }

    @Test
    @LoadFlows({ "flows/valids/approval-before-decision.yaml" })
    void shouldNotOpenTheRequestWhenApprovedAutomaticallyOrWhenATaskBeforeTheDecisionFails() throws Exception {
        Execution autoApproved = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "approval-before-decision", null, (flow, e) -> Map.of("auto", true));
        Execution failed = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "approval-before-decision", null, (flow, e) -> Map.of("fail", true));

        assertThat(autoApproved.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(failed.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        verify(approvalRequestHandler, never()).open(any(), argThat(e -> e.getId().equals(autoApproved.getId()) || e.getId().equals(failed.getId())), any(), any(), any());
    }

    @Test
    @LoadFlows({ "flows/valids/approval-before-decision.yaml" })
    void shouldFailTheTaskWhenTheRequestCannotBeOpened() throws Exception {
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT, "io.kestra.tests", "approval-before-decision", null,
            (flow, e) -> Map.of("failOpen", true),
            Duration.ofSeconds(15)
        );

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("approval").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getState().getHistories().stream().map(State.History::getState)).doesNotContain(State.Type.PAUSED);
        assertThat(execution.findTaskRunsByTaskId("approved")).isEmpty();
    }
}
