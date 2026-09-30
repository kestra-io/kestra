package io.kestra.core.runners;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.services.TaskOutputService;
import io.kestra.core.utils.MapUtils;
import io.kestra.plugin.core.flow.Approval;
import io.kestra.core.models.flows.State;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@KestraTest(startRunner = true)
class ApprovalRequestHandlerTest {
    @Inject
    TestRunnerUtils runnerUtils;

    @Inject
    ExecutionService executionService;

    @Inject
    FlowRepositoryInterface flowRepository;

    @Inject
    ApprovalRequestHandler handler;

    @Inject
    TaskOutputService taskOutputService;

    @MockBean
    @Replaces(ApprovalRequestHandler.NoopApprovalRequestHandler.class)
    ApprovalRequestHandler handler() throws Exception {
        ApprovalRequestHandler mock = mock(ApprovalRequestHandler.class);
        when(mock.open(any(), any(), any(), any(), any())).thenAnswer(invocation ->
        {
            TaskRun taskRun = invocation.getArgument(2);
            taskOutputService.saveOutputs(taskRun, MapUtils.merge(taskOutputService.getOutputs(taskRun), Map.of("written", "by-handler")));
            return new ApprovalRequestHandler.OpenedRequest("https://example.test/case/1", "case-1");
        });
        return mock;
    }

    @BeforeEach
    void clearHandlerInvocations() {
        clearInvocations(handler);
    }

    @Test
    @LoadFlows({ "flows/valids/approval-basic.yaml" })
    void shouldOpenOnceAndExposeTheHandlerLinkAndCaseInTheOutputs() throws Exception {
        Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "approval-basic", null, null, Duration.ofSeconds(30));

        verify(handler, times(1)).open(any(), any(), any(), any(), any());
        Map<String, Object> outputs = taskOutputService.getOutputs(execution.findTaskRunsByTaskId("approval").getFirst());
        assertThat(outputs.get("url")).isEqualTo("https://example.test/case/1");
        assertThat(outputs.get("caseId")).isEqualTo("case-1");
        assertThat(outputs.get("written")).isEqualTo("by-handler");
    }

    @Test
    @LoadFlows({ "flows/valids/approval-basic.yaml" })
    void shouldNotifyTheDecisionAndKeepTheCaseInTheOutputs() throws Exception {
        Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "approval-basic", null, null, Duration.ofSeconds(30));
        Flow flow = flowRepository.findByExecution(execution);
        String taskRunId = execution.findTaskRunsByTaskId("approval").getFirst().getId();

        Execution decided = executionService.decide(execution, flow, taskRunId, new Approval.Decision(Approval.Decision.Type.APPROVED, "ok"), Map.of());

        verify(handler, times(1)).decided(any(), any(), any(), eq(new Approval.Decision(Approval.Decision.Type.APPROVED, "ok")), any());
        assertThat(taskOutputService.getOutputs(decided.findTaskRunByTaskRunId(taskRunId)).get("caseId")).isEqualTo("case-1");
    }

    @Test
    @LoadFlows({ "flows/valids/approval-basic.yaml" })
    void shouldCloseTheRequestWhenItIsCancelled() throws Exception {
        Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "approval-basic", null, null, Duration.ofSeconds(30));
        Flow flow = flowRepository.findByExecution(execution);
        String taskRunId = execution.findTaskRunsByTaskId("approval").getFirst().getId();

        executionService.cancelApproval(execution, flow, taskRunId, null);

        ArgumentCaptor<TaskRun> closedTaskRun = ArgumentCaptor.forClass(TaskRun.class);
        verify(handler, times(1)).closed(any(), closedTaskRun.capture(), any(), eq(ApprovalRequestHandler.Resolution.CANCELLED));
        assertThat(taskOutputService.getOutputs(closedTaskRun.getValue()).get("caseId")).isEqualTo("case-1");
    }

    @Test
    @LoadFlows({ "flows/valids/approval-basic.yaml" })
    void shouldCloseTheRequestWhenThePausedExecutionIsKilled() throws Exception {
        Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "approval-basic", null, null, Duration.ofSeconds(30));
        Flow flow = flowRepository.findByExecution(execution);

        Execution killing = executionService.kill(execution, flow);

        assertThat(killing.getState().getCurrent()).isEqualTo(State.Type.KILLING);
        verify(handler, times(1)).closed(any(), any(), any(), eq(ApprovalRequestHandler.Resolution.KILLED));
    }

    @Test
    @LoadFlows({ "flows/valids/approval-kill-running-sibling.yaml" })
    void shouldCloseThePausedRequestWhenTheRunningExecutionIsKilled() throws Exception {
        Execution execution = runnerUtils.runOneUntil(
            MAIN_TENANT, "io.kestra.tests", "approval-kill-running-sibling",
            e -> e.findTaskRunsByTaskId("approval").stream().anyMatch(t -> t.getState().getCurrent() == State.Type.PAUSED)
                && e.findTaskRunsByTaskId("sleep").stream().anyMatch(t -> t.getState().getCurrent() == State.Type.RUNNING)
        );
        Flow flow = flowRepository.findByExecution(execution);

        Execution killing = executionService.kill(execution, flow);

        assertThat(killing.getState().getCurrent()).isEqualTo(State.Type.KILLING);
        verify(handler, times(1)).closed(any(), any(), any(), eq(ApprovalRequestHandler.Resolution.KILLED));
    }

    @Test
    @LoadFlows({ "flows/valids/approval-kill-onwait.yaml" })
    void shouldCloseTheRequestWhenTheExecutionIsKilledWhileTheApprovalRunsItsOnWait() throws Exception {
        Execution execution = runnerUtils.runOneUntil(
            MAIN_TENANT, "io.kestra.tests", "approval-kill-onwait",
            e -> e.findTaskRunsByTaskId("wait").stream().anyMatch(t -> t.getState().getCurrent() == State.Type.RUNNING)
        );
        Flow flow = flowRepository.findByExecution(execution);

        executionService.kill(execution, flow);

        verify(handler, times(1)).closed(any(), any(), any(), eq(ApprovalRequestHandler.Resolution.KILLED));
    }

    @Test
    @LoadFlows({ "flows/valids/approval-auto-approve.yaml" })
    void shouldNotOpenARequestWhenTheApprovalIsAutoApproved() throws Exception {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "approval-auto-approve", null, null, Duration.ofSeconds(30));

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        verify(handler, never()).open(any(), any(), any(), any(), any());
    }
}
