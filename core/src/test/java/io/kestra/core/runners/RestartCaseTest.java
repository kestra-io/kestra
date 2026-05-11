package io.kestra.core.runners;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.State.Type;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.ExecutionService;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Singleton
public class RestartCaseTest {
    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private TestRunnerUtils runnerUtils;

    @Inject
    private ExecutionService executionService;

    @Inject
    protected DispatchQueueInterface<Execution> executionQueue;

    public void restartFailedThenSuccess() throws Exception {
        Flow flow = flowRepository.findById(MAIN_TENANT, "io.kestra.tests", "restart_last_failed").orElseThrow();

        Execution firstExecution = runnerUtils.runOne(MAIN_TENANT, flow.getNamespace(), flow.getId());

        assertThat(firstExecution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(firstExecution.getTaskRunList()).hasSize(3);
        assertThat(firstExecution.getTaskRunList().get(2).getState().getCurrent()).isEqualTo(State.Type.FAILED);

        // wait
        Execution restartedExec = executionService.restart(firstExecution, flow, null);
        assertThat(restartedExec).isNotNull();
        assertThat(restartedExec.getId()).isEqualTo(firstExecution.getId());
        assertThat(restartedExec.getParentId()).isNull();
        assertThat(restartedExec.getTaskRunList().size()).isEqualTo(3);
        assertThat(restartedExec.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        Execution finishedRestartedExecution = runnerUtils.restartExecution(
            execution -> execution.getState().getCurrent() == State.Type.SUCCESS && execution.getId().equals(firstExecution.getId()),
            restartedExec
        );

        assertThat(finishedRestartedExecution).isNotNull();
        assertThat(finishedRestartedExecution.getId()).isEqualTo(firstExecution.getId());
        assertThat(finishedRestartedExecution.getParentId()).isNull();
        assertThat(finishedRestartedExecution.getTaskRunList().size()).isEqualTo(4);

        assertThat(finishedRestartedExecution.getTaskRunList().get(2).getAttempts().size()).isEqualTo(2);

        finishedRestartedExecution
            .getTaskRunList()
            .stream()
            .map(TaskRun::getState)
            .forEach(state -> assertThat(state.getCurrent()).isEqualTo(State.Type.SUCCESS));
    }

    public void restartFailedThenFailureWithGlobalErrors() throws Exception {
        Flow flow = flowRepository.findById(MAIN_TENANT, "io.kestra.tests", "restart_always_failed").orElseThrow();

        Execution firstExecution = runnerUtils.runOne(MAIN_TENANT, flow.getNamespace(), flow.getId(), Duration.ofSeconds(60));

        assertThat(firstExecution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(firstExecution.getTaskRunList()).hasSize(2);
        assertThat(firstExecution.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);

        // wait
        Execution restartedExec = executionService.restart(firstExecution, flow, null);

        assertThat(restartedExec).isNotNull();
        assertThat(restartedExec.getId()).isEqualTo(firstExecution.getId());
        assertThat(restartedExec.getParentId()).isNull();
        assertThat(restartedExec.getTaskRunList().size()).isEqualTo(1);
        assertThat(restartedExec.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        Execution finishedRestartedExecution = runnerUtils.restartExecution(
            execution -> execution.getState().getCurrent() == State.Type.FAILED && execution.getTaskRunList().getFirst().getAttempts().size() == 2,
            restartedExec
        );

        assertThat(finishedRestartedExecution).isNotNull();
        assertThat(finishedRestartedExecution.getId()).isEqualTo(firstExecution.getId());
        assertThat(finishedRestartedExecution.getParentId()).isNull();
        assertThat(finishedRestartedExecution.getTaskRunList().size()).isEqualTo(2);

        assertThat(finishedRestartedExecution.getTaskRunList().getFirst().getAttempts().size()).isEqualTo(2);

        assertThat(finishedRestartedExecution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    public void restartFailedThenFailureWithLocalErrors() throws Exception {
        Flow flow = flowRepository.findById(MAIN_TENANT, "io.kestra.tests", "restart_local_errors").orElseThrow();

        Execution firstExecution = runnerUtils.runOne(MAIN_TENANT, flow.getNamespace(), flow.getId(), Duration.ofSeconds(60));

        assertThat(firstExecution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(firstExecution.getTaskRunList()).hasSize(5);
        assertThat(firstExecution.getTaskRunList().get(3).getState().getCurrent()).isEqualTo(State.Type.FAILED);

        // wait
        Execution restartedExec = executionService.restart(firstExecution, flow, null);

        assertThat(restartedExec).isNotNull();
        assertThat(restartedExec.getId()).isEqualTo(firstExecution.getId());
        assertThat(restartedExec.getParentId()).isNull();
        assertThat(restartedExec.getTaskRunList().size()).isEqualTo(4);
        assertThat(restartedExec.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        Execution finishedRestartedExecution = runnerUtils.restartExecution(
            execution -> execution.getState().getCurrent() == State.Type.FAILED && execution.findTaskRunsByTaskId("failStep").stream().findFirst().get().getAttempts().size() == 2,
            restartedExec
        );

        assertThat(finishedRestartedExecution).isNotNull();
        assertThat(finishedRestartedExecution.getId()).isEqualTo(firstExecution.getId());
        assertThat(finishedRestartedExecution.getParentId()).isNull();
        assertThat(finishedRestartedExecution.getTaskRunList().size()).isEqualTo(5);

        Optional<TaskRun> taskRun = finishedRestartedExecution.findTaskRunsByTaskId("failStep").stream().findFirst();
        assertTrue(taskRun.isPresent());
        assertThat(taskRun.get().getAttempts().size()).isEqualTo(2);

        assertThat(finishedRestartedExecution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    public void replay() throws Exception {
        Flow flow = flowRepository.findById(MAIN_TENANT, "io.kestra.tests", "replay").orElseThrow();

        Execution firstExecution = runnerUtils.runOne(MAIN_TENANT, flow.getNamespace(), flow.getId(), Duration.ofSeconds(60));

        assertThat(firstExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // wait
        Execution restartedExec = executionService.replay(firstExecution, flow, null, null, Optional.empty());
        executionQueue.emit(restartedExec);

        assertThat(restartedExec.getState().getCurrent()).isEqualTo(Type.CREATED);
        assertThat(restartedExec.getState().getHistories()).hasSize(1);
        assertThat(restartedExec.getTaskRunList()).isEmpty();

        assertThat(restartedExec.getId()).isNotEqualTo(firstExecution.getId());
        Execution finishedRestartedExecution = runnerUtils.emitAndAwaitChildExecution(
            flow,
            firstExecution,
            restartedExec.withTenantId(MAIN_TENANT),
            Duration.ofSeconds(60)
        );

        assertThat(finishedRestartedExecution).isNotNull();
        assertThat(finishedRestartedExecution.getId()).isNotEqualTo(firstExecution.getId());
        assertThat(finishedRestartedExecution.getParentId()).isEqualTo(firstExecution.getId());
        assertThat(finishedRestartedExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    public void replayFromTaskId() throws Exception {
        Flow flow = flowRepository.findById(MAIN_TENANT, "io.kestra.tests", "replay").orElseThrow();

        Execution firstExecution = runnerUtils.runOne(MAIN_TENANT, flow.getNamespace(), flow.getId(), Duration.ofSeconds(60));

        assertThat(firstExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // wait
        Execution restartedExec = executionService.replay(firstExecution, flow, firstExecution.findTaskRunsByTaskId("log").getFirst().getId(), null, Optional.empty());
        executionQueue.emit(restartedExec);

        assertThat(restartedExec.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restartedExec.getState().getHistories()).hasSize(4);
        assertThat(restartedExec.getTaskRunList()).hasSize(2);
        assertThat(restartedExec.getTaskRunList().get(1).getState().getCurrent()).isEqualTo(State.Type.RESTARTED);

        assertThat(restartedExec.getId()).isNotEqualTo(firstExecution.getId());
        assertThat(restartedExec.getTaskRunList().get(1).getId()).isNotEqualTo(firstExecution.getTaskRunList().get(1).getId());
        Execution finishedRestartedExecution = runnerUtils.emitAndAwaitChildExecution(
            flow,
            firstExecution,
            restartedExec.withTenantId(MAIN_TENANT),
            Duration.ofSeconds(60)
        );

        assertThat(finishedRestartedExecution).isNotNull();
        assertThat(finishedRestartedExecution.getId()).isNotEqualTo(firstExecution.getId());
        assertThat(finishedRestartedExecution.getParentId()).isEqualTo(firstExecution.getId());
        assertThat(finishedRestartedExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    public void replayLoop() throws Exception {
        Flow flow = flowRepository.findById(MAIN_TENANT, "io.kestra.tests", "replay-loop").orElseThrow();

        Execution firstExecution = runnerUtils.runOne(MAIN_TENANT, flow.getNamespace(), flow.getId(), Duration.ofSeconds(60));

        assertThat(firstExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // wait
        Execution restartedExec = executionService.replay(firstExecution, flow, firstExecution.findTaskRunByTaskIdAndValue("2_end", List.of()).getId(), null, Optional.empty());
        executionQueue.emit(restartedExec);

        assertThat(restartedExec.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restartedExec.getState().getHistories()).hasSize(4);
        assertThat(restartedExec.getTaskRunList()).hasSize(2);
        assertThat(restartedExec.getTaskRunList().get(1).getState().getCurrent()).isEqualTo(State.Type.RESTARTED);

        assertThat(restartedExec.getId()).isNotEqualTo(firstExecution.getId());
        assertThat(restartedExec.getTaskRunList().get(1).getId()).isNotEqualTo(firstExecution.getTaskRunList().get(1).getId());
        Execution finishedRestartedExecution = runnerUtils.emitAndAwaitChildExecution(
            flow,
            firstExecution,
            restartedExec.withTenantId(MAIN_TENANT),
            Duration.ofSeconds(60)
        );

        assertThat(finishedRestartedExecution).isNotNull();
        assertThat(finishedRestartedExecution.getId()).isNotEqualTo(firstExecution.getId());
        assertThat(finishedRestartedExecution.getParentId()).isEqualTo(firstExecution.getId());
        assertThat(finishedRestartedExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    public void restartLoop() throws Exception {
        Flow flow = flowRepository.findById(MAIN_TENANT, "io.kestra.tests", "restart-loop").orElseThrow();

        Execution firstExecution = runnerUtils.runOne(MAIN_TENANT, flow.getNamespace(), flow.getId(), Duration.ofSeconds(60));

        assertThat(firstExecution.getState().getCurrent()).isEqualTo(Type.FAILED);
        var subExecutions = executionRepository.findLoopSubExecutions(firstExecution.getTenantId(), firstExecution.getId());
        assertThat(subExecutions).hasSize(1);

        // first restart: index=0 sub-execution is restarted and succeeds, then index=1 is created and fails
        Execution restarted1 = executionService.restart(firstExecution, flow, null);
        assertThat(restarted1.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        // History size: first run adds CREATED+RUNNING+FAILED (3), restart adds RESTARTED (1) = 4.
        // Each subsequent restart adds RUNNING+FAILED+RESTARTED (3) = 7, 10, ...
        assertThat(restarted1.getState().getHistories()).hasSize(4);
        assertThat(restarted1.getTaskRunList()).hasSize(1);
        assertThat(restarted1.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restarted1.getId()).isEqualTo(firstExecution.getId());
        assertThat(restarted1.getTaskRunList().getFirst().getId()).isEqualTo(firstExecution.getTaskRunList().getFirst().getId());

        // wait for the first restart to fail on the next iteration
        // use attemptNumber to distinguish this run from previous ones (same execution ID is reused)
        int attempt1 = restarted1.getMetadata().getAttemptNumber();
        Execution finishedRestarted1 = runnerUtils.emitAndAwaitExecution(
            e -> e.getId().equals(firstExecution.getId())
                && e.getMetadata().getAttemptNumber() == attempt1
                && executionService.isTerminated(flow, e),
            restarted1.withTenantId(MAIN_TENANT),
            Duration.ofSeconds(60)
        );
        assertThat(finishedRestarted1).isNotNull();
        assertThat(finishedRestarted1.getId()).isEqualTo(firstExecution.getId());
        assertThat(finishedRestarted1.getState().getCurrent()).isEqualTo(Type.FAILED);

        subExecutions = executionRepository.findLoopSubExecutions(firstExecution.getTenantId(), firstExecution.getId());
        assertThat(subExecutions).hasSize(2);

        // second restart: index=1 sub-execution is restarted and succeeds, then index=2 is created and fails
        Execution restarted2 = executionService.restart(finishedRestarted1, flow, null);
        assertThat(restarted2.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restarted2.getState().getHistories()).hasSize(7);
        assertThat(restarted2.getTaskRunList()).hasSize(1);
        assertThat(restarted2.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restarted2.getId()).isEqualTo(firstExecution.getId());
        assertThat(restarted2.getTaskRunList().getFirst().getId()).isEqualTo(firstExecution.getTaskRunList().getFirst().getId());

        // wait for the second restart to fail on the next iteration
        int attempt2 = restarted2.getMetadata().getAttemptNumber();
        Execution finishedRestarted2 = runnerUtils.emitAndAwaitExecution(
            e -> e.getId().equals(firstExecution.getId())
                && e.getMetadata().getAttemptNumber() == attempt2
                && executionService.isTerminated(flow, e),
            restarted2.withTenantId(MAIN_TENANT),
            Duration.ofSeconds(60)
        );
        assertThat(finishedRestarted2).isNotNull();
        assertThat(finishedRestarted2.getId()).isEqualTo(firstExecution.getId());
        assertThat(finishedRestarted2.getState().getCurrent()).isEqualTo(Type.FAILED);

        subExecutions = executionRepository.findLoopSubExecutions(firstExecution.getTenantId(), firstExecution.getId());
        assertThat(subExecutions).hasSize(3);

        // third restart: index=2 sub-execution is restarted and succeeds; all 3 iterations done
        Execution restarted3 = executionService.restart(finishedRestarted2, flow, null);
        assertThat(restarted3.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restarted3.getState().getHistories()).hasSize(10);
        assertThat(restarted3.getTaskRunList()).hasSize(1);
        assertThat(restarted3.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restarted3.getId()).isEqualTo(firstExecution.getId());
        assertThat(restarted3.getTaskRunList().getFirst().getId()).isEqualTo(firstExecution.getTaskRunList().getFirst().getId());

        // wait for the third restart to succeed
        int attempt3 = restarted3.getMetadata().getAttemptNumber();
        Execution finishedRestarted3 = runnerUtils.emitAndAwaitExecution(
            e -> e.getId().equals(firstExecution.getId())
                && e.getMetadata().getAttemptNumber() == attempt3
                && executionService.isTerminated(flow, e),
            restarted3.withTenantId(MAIN_TENANT),
            Duration.ofSeconds(60)
        );
        assertThat(finishedRestarted3).isNotNull();
        assertThat(finishedRestarted3.getId()).isEqualTo(firstExecution.getId());
        assertThat(finishedRestarted3.getState().getCurrent()).isEqualTo(Type.SUCCESS);

        // we must have 3 loop sub-executions at the end — one per iteration, each restarted in place
        subExecutions = executionRepository.findLoopSubExecutions(firstExecution.getTenantId(), firstExecution.getId());
        assertThat(subExecutions).hasSize(3);
    }

    public void restartMultiple() throws Exception {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "failed-first");
        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        Flow flow = flowRepository.findByExecution(execution);
        Execution restart = executionService.restart(execution, flow, null);
        assertThat(restart.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);

        Execution restartEnded = runnerUtils.restartExecution(
            e -> e.getState().getCurrent() == State.Type.FAILED,
            restart
        );

        assertThat(restartEnded.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        Execution newRestart = executionService.restart(restartEnded, flow, null);

        restartEnded = runnerUtils.restartExecution(
            e -> e.getState().getCurrent() == State.Type.FAILED,
            newRestart
        );

        assertThat(restartEnded.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    public void restartSubflow() throws Exception {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "restart-parent");
        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        // here we must have failed the subflow
        runnerUtils.awaitFlowExecution(e -> e.getState().getCurrent().isFailed(), MAIN_TENANT, "io.kestra.tests", "restart-child");

        // restart to end the subflow
        Flow flow = flowRepository.findByExecution(execution);
        Execution restarted1 = executionService.restart(execution, flow, null);
        execution = runnerUtils.restartExecution(
            e -> e.getState().getCurrent() == Type.SUCCESS && e.getFlowId().equals("restart-parent"),
            restarted1
        );
        assertThat(execution.getTaskRunList()).hasSize(3);

        runnerUtils.awaitFlowExecution(e -> e.getState().getCurrent().isSuccess(), MAIN_TENANT, "io.kestra.tests", "restart-child");
    }

    public void restartSubflowWithLoop() throws Exception {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "restart-parent-loop", Duration.ofSeconds(60));
        assertThat(execution.getTaskRunList()).hasSize(2); // hello + each
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        // here we must have 1 failed subflow
        runnerUtils.awaitFlowExecution(e -> e.getState().getCurrent().isFailed(), MAIN_TENANT, "io.kestra.tests", "restart-child");

        // there are 3 values so we must restart 3 times: each restart retries the failed sub-execution
        Flow flow = flowRepository.findByExecution(execution);
        Execution restarted1 = executionService.restart(execution, flow, null);
        execution = runnerUtils.restartExecution(
            e -> e.getState().getCurrent() == State.Type.FAILED && e.getFlowId().equals("restart-parent-loop")
                && e.getMetadata().getAttemptNumber() == 2,
            restarted1,
            Duration.ofSeconds(60)
        );
        Execution restarted2 = executionService.restart(execution, flow, null);
        execution = runnerUtils.restartExecution(
            e -> e.getState().getCurrent() == State.Type.FAILED && e.getFlowId().equals("restart-parent-loop")
                && e.getMetadata().getAttemptNumber() == 3,
            restarted2,
            Duration.ofSeconds(60)
        );
        Execution restarted3 = executionService.restart(execution, flow, null);
        execution = runnerUtils.restartExecution(
            e -> e.getState().getCurrent() == State.Type.SUCCESS && e.getFlowId().equals("restart-parent-loop"),
            restarted3,
            Duration.ofSeconds(60)
        );
        assertThat(execution.getTaskRunList()).hasSize(3);

        List<Execution> childExecutions = runnerUtils.awaitFlowExecutionNumber(3, MAIN_TENANT, "io.kestra.tests", "restart-child");
        List<Execution> successfulRestart = childExecutions.stream()
            .filter(e -> e.getState().getCurrent().equals(Type.SUCCESS)).toList();
        assertThat(successfulRestart).hasSize(3);
    }

    public void restartFailedWithFinally() throws Exception {
        Flow flow = flowRepository.findById(MAIN_TENANT, "io.kestra.tests", "restart-with-finally").orElseThrow();

        Execution firstExecution = runnerUtils.runOne(MAIN_TENANT, flow.getNamespace(), flow.getId(), Duration.ofSeconds(60));

        assertThat(firstExecution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(firstExecution.getTaskRunList()).hasSize(3);
        assertThat(firstExecution.getTaskRunList().get(1).getState().getCurrent()).isEqualTo(State.Type.FAILED);

        // wait
        Execution restartedExec = executionService.restart(firstExecution, flow, null);
        assertThat(restartedExec).isNotNull();
        assertThat(restartedExec.getId()).isEqualTo(firstExecution.getId());
        assertThat(restartedExec.getParentId()).isNull();
        assertThat(restartedExec.getTaskRunList().size()).isEqualTo(2);
        assertThat(restartedExec.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        Execution finishedRestartedExecution = runnerUtils.restartExecution(
            execution -> executionService.isTerminated(flow, execution) && execution.getState().isSuccess(),
            restartedExec
        );

        assertThat(finishedRestartedExecution).isNotNull();
        assertThat(finishedRestartedExecution.getId()).isEqualTo(firstExecution.getId());
        assertThat(finishedRestartedExecution.getParentId()).isNull();
        assertThat(finishedRestartedExecution.getTaskRunList().size()).isEqualTo(4);

        finishedRestartedExecution
            .getTaskRunList()
            .stream()
            .map(TaskRun::getState)
            .forEach(state -> assertThat(state.getCurrent()).isIn(State.Type.SUCCESS, State.Type.SKIPPED));
    }

    public void restartFailedWithAfterExecution() throws Exception {
        Flow flow = flowRepository.findById(MAIN_TENANT, "io.kestra.tests", "restart-with-after-execution").orElseThrow();

        Execution firstExecution = runnerUtils.runOne(MAIN_TENANT, flow.getNamespace(), flow.getId(), Duration.ofSeconds(60));

        assertThat(firstExecution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(firstExecution.getTaskRunList()).hasSize(3);
        assertThat(firstExecution.getTaskRunList().get(1).getState().getCurrent()).isEqualTo(State.Type.FAILED);

        // wait
        Execution restartedExec = executionService.restart(firstExecution, flow, null);
        assertThat(restartedExec).isNotNull();
        assertThat(restartedExec.getId()).isEqualTo(firstExecution.getId());
        assertThat(restartedExec.getParentId()).isNull();
        assertThat(restartedExec.getTaskRunList().size()).isEqualTo(2);
        assertThat(restartedExec.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);

        Execution finishedRestartedExecution = runnerUtils.restartExecution(
            execution -> executionService.isTerminated(flow, execution) && execution.getState().isSuccess(),
            restartedExec
        );
        assertThat(finishedRestartedExecution).isNotNull();
        assertThat(finishedRestartedExecution.getId()).isEqualTo(firstExecution.getId());
        assertThat(finishedRestartedExecution.getParentId()).isNull();
        assertThat(finishedRestartedExecution.getTaskRunList().size()).isEqualTo(4);

        finishedRestartedExecution
            .getTaskRunList()
            .stream()
            .map(TaskRun::getState)
            .forEach(state -> assertThat(state.getCurrent()).isIn(State.Type.SUCCESS, State.Type.SKIPPED));
    }

    public void restartOrReplayLoopUntil() throws Exception {
        Flow flow = flowRepository.findById(MAIN_TENANT, "io.kestra.tests", "loop-until-restart").orElseThrow();

        Execution firstExecution = runnerUtils.runOne(MAIN_TENANT, flow.getNamespace(), flow.getId(), Duration.ofSeconds(60));
        assertThat(firstExecution.getState().getCurrent()).isEqualTo(Type.FAILED);

        // restarting case
        Execution restartedExecution = executionService.restart(firstExecution, flow, null);
        assertThat(restartedExecution).isNotNull();
        assertThat(restartedExecution.getId()).isEqualTo(firstExecution.getId());
        assertThat(restartedExecution.getState().getCurrent()).isEqualTo(Type.RESTARTED);

        Execution finalRestartedExecution = runnerUtils.restartExecution(execution -> execution.getState().isFailed(), restartedExecution);
        assertThat(finalRestartedExecution.getState().getCurrent()).isEqualTo(Type.FAILED);

        Optional<TaskRun> parentTaskRun1 = finalRestartedExecution.findTaskRunsByTaskId("loop_test").stream().findFirst();
        assertThat(parentTaskRun1.isPresent());

        State.History lastState1 = parentTaskRun1.get().getState().getHistories().getLast();
        State.History lastRestarted1 = parentTaskRun1.get().getState().getHistories().reversed().stream()
            .filter(history -> history.getState() == Type.RESTARTED).findFirst().get();
        assertThat(lastRestarted1).isNotNull();
        assertThat(lastRestarted1.getDate().plus(3, ChronoUnit.SECONDS)).isBefore(lastState1.getDate());

        // replaying case
        Execution replayedExecution = executionService.replay(firstExecution, flow, firstExecution.findTaskRunByTaskIdAndValue("loop_test", List.of()).getId(), null, Optional.empty());
        assertThat(replayedExecution.getState().getCurrent()).isEqualTo(Type.RESTARTED);
        assertThat(replayedExecution.getId()).isNotEqualTo(firstExecution.getId());
        executionQueue.emit(replayedExecution);

        Execution finalReplayedExecution = runnerUtils.awaitExecution(
            execution -> execution.getState().isTerminated(),
            replayedExecution
        );
        assertThat(finalReplayedExecution.getState().getCurrent()).isEqualTo(Type.FAILED);

        Optional<TaskRun> parentTaskRun2 = finalReplayedExecution.findTaskRunsByTaskId("loop_test").stream().findFirst();
        assertThat(parentTaskRun2.isPresent());

        State.History lastState2 = parentTaskRun2.get().getState().getHistories().getLast();
        State.History lastRestarted2 = parentTaskRun2.get().getState().getHistories().reversed().stream()
            .filter(history -> history.getState() == Type.RESTARTED).findFirst().get();
        assertThat(lastRestarted2).isNotNull();
        assertThat(lastRestarted2.getDate().plus(3, ChronoUnit.SECONDS)).isBefore(lastState2.getDate());
    }

}
