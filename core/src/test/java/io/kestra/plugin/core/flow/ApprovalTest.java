package io.kestra.plugin.core.flow;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.services.TaskOutputService;
import io.kestra.core.utils.ListUtils;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@KestraTest(startRunner = true)
public class ApprovalTest {

    @Inject
    TestRunnerUtils runnerUtils;
    @Inject
    Suite suite;
    @Inject
    ExecutionService executionService;
    @Inject
    FlowRepositoryInterface flowRepository;
    @Inject
    TaskOutputService taskOutputService;

    @Test
    @LoadFlows({ "flows/valids/approval-basic.yaml" })
    void shouldRunOnApproveWhenApproved() throws Exception {
        Map<String, Object> outputs = suite.approve(runnerUtils);
        assertThat((String) outputs.get("url")).contains("/executions/");
    }

    @Test
    @LoadFlows({ "flows/valids/approval-basic.yaml" })
    void shouldRunOnDenyWhenDenied() throws Exception {
        Map<String, Object> outputs = suite.deny(runnerUtils);
        assertThat((String) outputs.get("url")).contains("/executions/");
    }

    @Test
    @LoadFlows({ "flows/valids/approval-comment-always.yaml" })
    void shouldThrowWhenCommentRequiredAlwaysAndCommentMissing() throws Exception {
        Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "approval-comment-always", null, null, Duration.ofSeconds(30));
        Flow flow = flowRepository.findByExecution(execution);
        String taskRunId = execution.findTaskRunsByTaskId("approval").getFirst().getId();

        assertThatThrownBy(() -> executionService.decide(
            execution, flow, taskRunId, new Approval.Decision(Approval.Decision.Type.APPROVED, null), Map.of()
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @LoadFlows({ "flows/valids/approval-comment-on-deny.yaml" })
    void shouldAllowApprovalWithoutCommentWhenCommentRequiredOnDeny() throws Exception {
        Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "approval-comment-on-deny", null, null, Duration.ofSeconds(30));
        String executionId = execution.getId();
        Flow flow = flowRepository.findByExecution(execution);
        String taskRunId = execution.findTaskRunsByTaskId("approval").getFirst().getId();

        Execution decided = executionService.decide(
            execution, flow, taskRunId, new Approval.Decision(Approval.Decision.Type.APPROVED, null), Map.of()
        );

        execution = runnerUtils.emitAndAwaitExecution(
            e -> e.getId().equals(executionId) && e.getState().getCurrent() == State.Type.SUCCESS,
            decided
        );

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows({ "flows/valids/approval-comment-on-deny.yaml" })
    void shouldThrowWhenCommentRequiredOnDenyAndDenyingWithoutComment() throws Exception {
        Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "approval-comment-on-deny", null, null, Duration.ofSeconds(30));
        Flow flow = flowRepository.findByExecution(execution);
        String taskRunId = execution.findTaskRunsByTaskId("approval").getFirst().getId();

        assertThatThrownBy(() -> executionService.decide(
            execution, flow, taskRunId, new Approval.Decision(Approval.Decision.Type.DENIED, null), Map.of()
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @LoadFlows({ "flows/valids/approval-inputs.yaml" })
    @SuppressWarnings("unchecked")
    void shouldStoreDecisionInputsInOutputs() throws Exception {
        Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "approval-inputs", null, null, Duration.ofSeconds(30));
        String executionId = execution.getId();
        Flow flow = flowRepository.findByExecution(execution);
        String taskRunId = execution.findTaskRunsByTaskId("approval").getFirst().getId();

        Execution decided = executionService.decide(
            execution, flow, taskRunId, new Approval.Decision(Approval.Decision.Type.APPROVED, "ok"), Map.of("reason", "urgent fix")
        );

        execution = runnerUtils.emitAndAwaitExecution(
            e -> e.getId().equals(executionId) && e.getState().getCurrent() == State.Type.SUCCESS,
            decided
        );

        Map<String, Object> outputs = taskOutputService.getOutputs(execution.findTaskRunsByTaskId("approval").getFirst());
        assertThat((Map<String, Object>) outputs.get("inputs")).containsEntry("reason", "urgent fix");
    }

    @Test
    @LoadFlows({ "flows/valids/approval-empty-wait.yaml" })
    void shouldPauseImmediatelyWhenOnWaitIsEmpty() throws Exception {
        Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "approval-empty-wait", null, null, Duration.ofSeconds(30));

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.PAUSED);
    }

    @Test
    @LoadFlows({ "flows/valids/approval-parallel.yaml" })
    void shouldPauseSiblingApprovalsIndependently() throws Exception {
        Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "approval-parallel", null, null, Duration.ofSeconds(30));
        String executionId = execution.getId();
        Flow flow = flowRepository.findByExecution(execution);

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.PAUSED);
        assertThat(execution.findTaskRunsByTaskId("approvalA").getFirst().getState().getCurrent()).isEqualTo(State.Type.PAUSED);
        assertThat(execution.findTaskRunsByTaskId("approvalB").getFirst().getState().getCurrent()).isEqualTo(State.Type.PAUSED);

        String taskRunIdA = execution.findTaskRunsByTaskId("approvalA").getFirst().getId();
        String taskRunIdB = execution.findTaskRunsByTaskId("approvalB").getFirst().getId();

        Execution decidedA = executionService.decide(
            execution, flow, taskRunIdA, new Approval.Decision(Approval.Decision.Type.APPROVED, "ok"), Map.of()
        );
        Execution decidedB = executionService.decide(
            decidedA, flow, taskRunIdB, new Approval.Decision(Approval.Decision.Type.APPROVED, "ok"), Map.of()
        );

        execution = runnerUtils.emitAndAwaitExecution(
            e -> e.getId().equals(executionId) && e.getState().getCurrent() == State.Type.SUCCESS,
            decidedB
        );

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("approvedA")).hasSize(1);
        assertThat(execution.findTaskRunsByTaskId("approvedB")).hasSize(1);
    }

    @Test
    @LoadFlows({ "flows/valids/approval-url-merge.yaml" })
    void shouldExposeUrlToOnApproveTasks() throws Exception {
        Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "approval-url-merge", null, null, Duration.ofSeconds(30));
        String executionId = execution.getId();
        Flow flow = flowRepository.findByExecution(execution);
        String taskRunId = execution.findTaskRunsByTaskId("approval").getFirst().getId();

        Execution decided = executionService.decide(
            execution, flow, taskRunId, new Approval.Decision(Approval.Decision.Type.APPROVED, "ok"), Map.of()
        );

        execution = runnerUtils.emitAndAwaitExecution(
            e -> e.getId().equals(executionId) && e.getState().getCurrent() == State.Type.SUCCESS,
            decided
        );

        Map<String, Object> approvedOutputs = taskOutputService.getOutputs(execution.findTaskRunsByTaskId("approved").getFirst());
        assertThat((String) approvedOutputs.get("value")).contains("/executions/");
    }

    @Test
    @LoadFlows({ "flows/valids/approval-basic.yaml" })
    void shouldRecordReviewerIdentityWhenResumedProvided() throws Exception {
        Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "approval-basic", null, null, Duration.ofSeconds(30));
        String executionId = execution.getId();
        Flow flow = flowRepository.findByExecution(execution);
        String taskRunId = execution.findTaskRunsByTaskId("approval").getFirst().getId();

        Execution decided = executionService.decide(
            execution, flow, taskRunId, new Approval.Decision(Approval.Decision.Type.APPROVED, "ok"), Map.of(),
            Pause.Resumed.now("alice", State.Type.RUNNING)
        );

        execution = runnerUtils.emitAndAwaitExecution(
            e -> e.getId().equals(executionId) && e.getState().getCurrent() == State.Type.SUCCESS,
            decided
        );

        Map<String, Object> outputs = taskOutputService.getOutputs(execution.findTaskRunsByTaskId("approval").getFirst());
        assertThat(outputs.get("by")).isEqualTo("alice");
    }

    @Test
    @LoadFlows({ "flows/valids/approval-basic.yaml" })
    void shouldRefuseASecondDecisionOnTheSameTaskRun() throws Exception {
        Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "approval-basic", null, null, Duration.ofSeconds(30));
        Flow flow = flowRepository.findByExecution(execution);
        String taskRunId = execution.findTaskRunsByTaskId("approval").getFirst().getId();

        Execution decided = executionService.decide(
            execution, flow, taskRunId, new Approval.Decision(Approval.Decision.Type.APPROVED, "ok"), Map.of()
        );

        assertThatThrownBy(() -> executionService.decide(
            decided, flow, taskRunId, new Approval.Decision(Approval.Decision.Type.DENIED, "changed my mind"), Map.of()
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("is not paused");
    }

    @Test
    @LoadFlows({ "flows/valids/approval-auto-approve.yaml" })
    void shouldSkipWaitAndApproveAutomaticallyWhenAutoApproveIsTrue() throws Exception {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "approval-auto-approve");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getState().getHistories().stream().map(State.History::getState))
            .doesNotContain(State.Type.PAUSING, State.Type.PAUSED);
        assertThat(execution.findTaskRunsByTaskId("notify")).isEmpty();
        assertThat(execution.findTaskRunsByTaskId("approved")).hasSize(1);
        assertThat(execution.findTaskRunsByTaskId("denied")).isEmpty();

        Map<String, Object> outputs = taskOutputService.getOutputs(execution.findTaskRunsByTaskId("approval").getFirst());
        assertThat(outputs.get("decision")).isEqualTo("APPROVED");
        assertThat(outputs.get("auto")).isEqualTo(true);
        assertThat(outputs.get("by")).isNull();
    }

    @Test
    @LoadFlows({ "flows/valids/approval-auto-approve-conditional.yaml" })
    void shouldRenderAutoApprovePerExecutionNotFromAFirstRenderCache() throws Exception {
        Execution first = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "approval-auto-approve-conditional", null,
            (flow, execution) -> Map.of("autoApprove", true));
        assertThat(first.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        Execution second = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "approval-auto-approve-conditional", null,
            (flow, execution) -> Map.of("autoApprove", false), Duration.ofSeconds(30));
        assertThat(second.getState().getCurrent()).isEqualTo(State.Type.PAUSED);
    }

    @Test
    @LoadFlows({ "flows/valids/approval-expire.yaml" })
    void shouldExpireWithDefaultBehaviorWhenTimeoutElapses() throws Exception {
        Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "approval-expire", null, null, Duration.ofSeconds(30));
        String executionId = execution.getId();
        Instant pausedAt = execution.getState().getHistories().stream()
            .filter(history -> history.getState() == State.Type.PAUSED)
            .findFirst()
            .orElseThrow()
            .getDate();

        execution = runnerUtils.awaitExecution(
            e -> e.getId().equals(executionId) && e.getState().isTerminated()
                && ListUtils.emptyOnNull(e.getTaskRunList()).stream().allMatch(t -> t.getState().isTerminated()),
            execution,
            Duration.ofSeconds(30)
        );

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.CANCELLED);
        assertThat(execution.findTaskRunsByTaskId("approved")).isEmpty();
        assertThat(execution.findTaskRunsByTaskId("denied")).isEmpty();
        TaskRun cleanup = execution.findTaskRunsByTaskId("cleanup").getFirst();
        assertThat(cleanup.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        TaskRun approval = execution.findTaskRunsByTaskId("approval").getFirst();
        assertThat(approval.getState().getEndDate()).isPresent();
        assertThat(cleanup.getState().getEndDate()).isPresent();
        assertThat(approval.getState().getEndDate().get()).isAfterOrEqualTo(cleanup.getState().getEndDate().get());

        Map<String, Object> outputs = taskOutputService.getOutputs(approval);
        assertThat(outputs.get("decision")).isEqualTo("EXPIRED");
        assertThat(outputs.get("auto")).isEqualTo(false);
        assertThat(outputs.get("by")).isNull();
        assertThat((String) outputs.get("due")).isNotNull();
        Instant due = Instant.parse((String) outputs.get("due"));
        assertThat(due).isCloseTo(pausedAt.plusSeconds(1), within(500, ChronoUnit.MILLIS));
    }

    @Test
    @LoadFlows({ "flows/valids/approval-expire-no-finally.yaml" })
    void shouldExpireWithDefaultBehaviorWhenNoFinallyIsConfigured() throws Exception {
        Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "approval-expire-no-finally", null, null, Duration.ofSeconds(30));
        String executionId = execution.getId();

        execution = runnerUtils.awaitExecution(
            e -> e.getId().equals(executionId) && e.getState().isTerminated(),
            execution,
            Duration.ofSeconds(30)
        );

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.CANCELLED);
        assertThat(execution.findTaskRunsByTaskId("approved")).isEmpty();
        assertThat(execution.findTaskRunsByTaskId("denied")).isEmpty();

        Map<String, Object> outputs = taskOutputService.getOutputs(execution.findTaskRunsByTaskId("approval").getFirst());
        assertThat(outputs.get("decision")).isEqualTo("EXPIRED");
    }

    @Test
    @LoadFlows({ "flows/valids/approval-expire-parallel-sibling.yaml" })
    void shouldNotExpireADecisionAlreadyMadeWhileASiblingIsStillRunning() throws Exception {
        Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "approval-expire-parallel-sibling", null, null, Duration.ofSeconds(30));
        String executionId = execution.getId();
        Flow flow = flowRepository.findByExecution(execution);
        String taskRunId = execution.findTaskRunsByTaskId("approval").getFirst().getId();

        Execution decided = executionService.decide(
            execution, flow, taskRunId, new Approval.Decision(Approval.Decision.Type.APPROVED, "ok"), Map.of()
        );

        // The sleeping sibling keeps the execution RUNNING well past the approval's timeout, so the
        // matured delay is processed while the execution is NOT terminated and the approval task run is
        // no longer PAUSED — exactly the case the processor's not-currently-paused guard must catch.
        execution = runnerUtils.emitAndAwaitExecution(
            e -> e.getId().equals(executionId) && e.getState().getCurrent() == State.Type.SUCCESS,
            decided
        );

        Map<String, Object> outputs = taskOutputService.getOutputs(execution.findTaskRunsByTaskId("approval").getFirst());
        assertThat(outputs.get("decision")).isEqualTo("APPROVED");
        assertThat(execution.findTaskRunsByTaskId("approved")).hasSize(1);
    }

    @Test
    @LoadFlows({ "flows/valids/approval-deny-behavior-cancel.yaml" })
    void shouldCancelExecutionOnDenyByDefault() throws Exception {
        Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "approval-deny-behavior-cancel", null, null, Duration.ofSeconds(30));
        String executionId = execution.getId();
        Flow flow = flowRepository.findByExecution(execution);
        String taskRunId = execution.findTaskRunsByTaskId("approval").getFirst().getId();

        Execution decided = executionService.decide(
            execution, flow, taskRunId, new Approval.Decision(Approval.Decision.Type.DENIED, "no"), Map.of()
        );

        execution = runnerUtils.emitAndAwaitExecution(
            e -> e.getId().equals(executionId) && e.getState().isTerminated(),
            decided
        );

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.CANCELLED);
        assertThat(execution.findTaskRunsByTaskId("denied")).hasSize(1);
        assertThat(execution.findTaskRunsByTaskId("after")).isEmpty();
    }

    @Test
    @LoadFlows({ "flows/valids/approval-approve-behavior-succeed-with-failing-onapprove.yaml" })
    void shouldNotHideAFailingBranchBehindTheConfiguredBehavior() throws Exception {
        Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "approval-approve-behavior-succeed-with-failing-onapprove", null, null, Duration.ofSeconds(30));
        String executionId = execution.getId();
        Flow flow = flowRepository.findByExecution(execution);
        String taskRunId = execution.findTaskRunsByTaskId("approval").getFirst().getId();

        Execution decided = executionService.decide(
            execution, flow, taskRunId, new Approval.Decision(Approval.Decision.Type.APPROVED, "ok"), Map.of()
        );

        execution = runnerUtils.emitAndAwaitExecution(
            e -> e.getId().equals(executionId) && e.getState().isTerminated(),
            decided
        );

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    @Test
    @LoadFlows({ "flows/valids/approval-deny-behavior-kill.yaml" })
    void shouldKillRunningSiblingsWhenDenyBehaviorIsKill() throws Exception {
        Execution execution = runnerUtils.runOneUntil(
            MAIN_TENANT, "io.kestra.tests", "approval-deny-behavior-kill", null, null, Duration.ofSeconds(30),
            e -> e.findTaskRunsByTaskId("approval").stream().anyMatch(t -> t.getState().getCurrent() == State.Type.PAUSED)
                && e.findTaskRunsByTaskId("sleep").stream().anyMatch(t -> t.getState().isRunning())
        );
        String executionId = execution.getId();
        Flow flow = flowRepository.findByExecution(execution);
        String taskRunId = execution.findTaskRunsByTaskId("approval").getFirst().getId();

        Execution decided = executionService.decide(
            execution, flow, taskRunId, new Approval.Decision(Approval.Decision.Type.DENIED, "no"), Map.of()
        );

        execution = runnerUtils.emitAndAwaitExecution(
            e -> e.getId().equals(executionId) && e.getState().isTerminated()
                && e.findTaskRunsByTaskId("sleep").stream().allMatch(t -> t.getState().isTerminated()),
            decided
        );

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.KILLED);
        assertThat(execution.findTaskRunsByTaskId("sleep").getFirst().getState().getCurrent()).isEqualTo(State.Type.KILLED);
    }

    @Test
    @LoadFlows({ "flows/valids/approval-deny-behavior-cancel-with-running-sibling.yaml" })
    void shouldLetARunningSiblingFinishWhenDenyBehaviorIsCancel() throws Exception {
        Execution execution = runnerUtils.runOneUntil(
            MAIN_TENANT, "io.kestra.tests", "approval-deny-behavior-cancel-with-running-sibling", null, null, Duration.ofSeconds(30),
            e -> e.findTaskRunsByTaskId("approval").stream().anyMatch(t -> t.getState().getCurrent() == State.Type.PAUSED)
                && e.findTaskRunsByTaskId("sleep").stream().anyMatch(t -> t.getState().isRunning())
        );
        String executionId = execution.getId();
        Flow flow = flowRepository.findByExecution(execution);
        String taskRunId = execution.findTaskRunsByTaskId("approval").getFirst().getId();

        Execution decided = executionService.decide(
            execution, flow, taskRunId, new Approval.Decision(Approval.Decision.Type.DENIED, "no"), Map.of()
        );

        execution = runnerUtils.emitAndAwaitExecution(
            e -> e.getId().equals(executionId) && e.getState().isTerminated()
                && e.findTaskRunsByTaskId("sleep").stream().allMatch(t -> t.getState().isTerminated()),
            decided,
            Duration.ofSeconds(30)
        );

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.CANCELLED);
        assertThat(execution.findTaskRunsByTaskId("sleep").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows({ "flows/valids/approval-parallel.yaml" })
    void shouldCancelTheWholeExecutionEvenWhenASiblingApprovalIsApproved() throws Exception {
        Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "approval-parallel", null, null, Duration.ofSeconds(30));
        String executionId = execution.getId();
        Flow flow = flowRepository.findByExecution(execution);
        String taskRunIdA = execution.findTaskRunsByTaskId("approvalA").getFirst().getId();
        String taskRunIdB = execution.findTaskRunsByTaskId("approvalB").getFirst().getId();

        // markAs keeps the execution PAUSED while any sibling is still PAUSED (shared with Pause), so
        // neither branch runs until both are decided.
        Execution decidedA = executionService.decide(
            execution, flow, taskRunIdA, new Approval.Decision(Approval.Decision.Type.DENIED, "no"), Map.of()
        );
        Execution decidedB = executionService.decide(
            decidedA, flow, taskRunIdB, new Approval.Decision(Approval.Decision.Type.APPROVED, "ok"), Map.of()
        );

        execution = runnerUtils.emitAndAwaitExecution(
            e -> e.getId().equals(executionId) && e.getState().isTerminated(),
            decidedB
        );

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.CANCELLED);
        assertThat(execution.findTaskRunsByTaskId("deniedA")).hasSize(1);
    }

    @Test
    @LoadFlows({ "flows/valids/approval-approve-behavior-succeed.yaml" })
    void shouldSucceedTheWholeExecutionWhenApproveBehaviorIsSucceed() throws Exception {
        Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "approval-approve-behavior-succeed", null, null, Duration.ofSeconds(30));
        String executionId = execution.getId();
        Flow flow = flowRepository.findByExecution(execution);
        String taskRunId = execution.findTaskRunsByTaskId("approval").getFirst().getId();

        Execution decided = executionService.decide(
            execution, flow, taskRunId, new Approval.Decision(Approval.Decision.Type.APPROVED, "ok"), Map.of()
        );

        execution = runnerUtils.emitAndAwaitExecution(
            e -> e.getId().equals(executionId) && e.getState().isTerminated(),
            decided
        );

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("approved")).hasSize(1);
        assertThat(execution.findTaskRunsByTaskId("after")).isEmpty();
    }

    @Test
    @LoadFlows({ "flows/valids/approval-approve-behavior-fail.yaml" })
    void shouldFailTheWholeExecutionThroughFlowLevelErrorsWhenApproveBehaviorIsFail() throws Exception {
        Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "approval-approve-behavior-fail", null, null, Duration.ofSeconds(30));
        String executionId = execution.getId();
        Flow flow = flowRepository.findByExecution(execution);
        String taskRunId = execution.findTaskRunsByTaskId("approval").getFirst().getId();

        Execution decided = executionService.decide(
            execution, flow, taskRunId, new Approval.Decision(Approval.Decision.Type.APPROVED, "ok"), Map.of()
        );

        execution = runnerUtils.emitAndAwaitExecution(
            e -> e.getId().equals(executionId) && e.getState().isTerminated(),
            decided
        );

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("approved")).hasSize(1);
        assertThat(execution.findTaskRunsByTaskId("approval").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("cleanup")).hasSize(1);
    }

    @Test
    @LoadFlows({ "flows/valids/approval-finally-fail.yaml" })
    void shouldRunFinallyOnceWhenOnWaitFailsWithoutAllowFailure() throws Exception {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "approval-finally-fail");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("cleanup")).hasSize(1);
        assertThat(execution.findTaskRunsByTaskId("approved")).isEmpty();
        assertThat(execution.findTaskRunsByTaskId("denied")).isEmpty();
    }

    @Test
    @LoadFlows({ "flows/valids/approval-finally-allow-failure.yaml" })
    void shouldPauseAndRunFinallyOnceWhenOnWaitFailsWithAllowFailure() throws Exception {
        Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "approval-finally-allow-failure", null, null, Duration.ofSeconds(30));
        String executionId = execution.getId();
        Flow flow = flowRepository.findByExecution(execution);
        String taskRunId = execution.findTaskRunsByTaskId("approval").getFirst().getId();

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.PAUSED);
        assertThat(execution.findTaskRunsByTaskId("cleanup")).isEmpty();

        Execution decided = executionService.decide(
            execution, flow, taskRunId, new Approval.Decision(Approval.Decision.Type.APPROVED, "ok"), Map.of()
        );

        execution = runnerUtils.emitAndAwaitExecution(
            e -> e.getId().equals(executionId) && e.getState().getCurrent() == State.Type.SUCCESS,
            decided
        );

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("approved")).hasSize(1);
        assertThat(execution.findTaskRunsByTaskId("cleanup")).hasSize(1);
    }

    @Test
    @LoadFlows({ "flows/valids/approval-finally-success.yaml" })
    void shouldRunFinallyOnceAfterBranchOnSuccessPath() throws Exception {
        Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "approval-finally-success", null, null, Duration.ofSeconds(30));
        String executionId = execution.getId();
        Flow flow = flowRepository.findByExecution(execution);
        String taskRunId = execution.findTaskRunsByTaskId("approval").getFirst().getId();

        assertThat(execution.findTaskRunsByTaskId("cleanup")).isEmpty();

        Execution decided = executionService.decide(
            execution, flow, taskRunId, new Approval.Decision(Approval.Decision.Type.APPROVED, "ok"), Map.of()
        );

        execution = runnerUtils.emitAndAwaitExecution(
            e -> e.getId().equals(executionId) && e.getState().getCurrent() == State.Type.SUCCESS,
            decided
        );

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("approved")).hasSize(1);
        assertThat(execution.findTaskRunsByTaskId("cleanup")).hasSize(1);
    }

    @Singleton
    public static class Suite {
        @Inject
        ExecutionService executionService;

        @Inject
        FlowRepositoryInterface flowRepository;

        @Inject
        TaskOutputService taskOutputService;

        public Map<String, Object> approve(TestRunnerUtils runnerUtils) throws Exception {
            Map<String, Object> outputs = decide(runnerUtils, Approval.Decision.Type.APPROVED, "looks good");

            assertThat(outputs.get("decision")).isEqualTo("APPROVED");
            assertThat(outputs.get("comment")).isEqualTo("looks good");

            return outputs;
        }

        public Map<String, Object> deny(TestRunnerUtils runnerUtils) throws Exception {
            Map<String, Object> outputs = decide(runnerUtils, Approval.Decision.Type.DENIED, "needs changes");

            assertThat(outputs.get("decision")).isEqualTo("DENIED");
            assertThat(outputs.get("comment")).isEqualTo("needs changes");

            return outputs;
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> decide(TestRunnerUtils runnerUtils, Approval.Decision.Type decisionType, String comment) throws Exception {
            // Given — the execution runs the Approval's onWait tasks before pausing
            Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "approval-basic", null, null, Duration.ofSeconds(30));
            String executionId = execution.getId();
            Flow flow = flowRepository.findByExecution(execution);

            // Then — the execution was PAUSING while onWait ran, then PAUSED once it finished
            assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.PAUSED);
            assertThat(execution.getState().getHistories().stream().map(State.History::getState)).contains(State.Type.PAUSING);
            assertThat(execution.findTaskRunsByTaskId("notify")).hasSize(1);
            assertThat(execution.findTaskRunsByTaskId("notify").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

            String approvalTaskRunId = execution.findTaskRunsByTaskId("approval").getFirst().getId();

            // When — the request is decided
            Execution decided = executionService.decide(
                execution,
                flow,
                approvalTaskRunId,
                new Approval.Decision(decisionType, comment),
                Map.of()
            );

            execution = runnerUtils.emitAndAwaitExecution(
                e -> e.getId().equals(executionId) && e.getState().isTerminated(),
                decided
            );

            // Then — only the decided branch ran; the default denyBehavior (CANCEL) ends a denial CANCELLED.
            if (decisionType == Approval.Decision.Type.APPROVED) {
                assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
                assertThat(execution.findTaskRunsByTaskId("approved")).hasSize(1);
                assertThat(execution.findTaskRunsByTaskId("denied")).isEmpty();
            } else {
                assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.CANCELLED);
                assertThat(execution.findTaskRunsByTaskId("denied")).hasSize(1);
                assertThat(execution.findTaskRunsByTaskId("approved")).isEmpty();
            }

            Map<String, Object> outputs = taskOutputService.getOutputs(execution.findTaskRunsByTaskId("approval").getFirst());
            assertThat(outputs.get("auto")).isEqualTo(false);
            assertThat(outputs.get("by")).isNull();
            assertThat(outputs.get("on")).isNotNull();

            return outputs;
        }
    }
}
