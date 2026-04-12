package io.kestra.plugin.core.flow;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LoopRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.services.TaskOutputService;
import io.micronaut.data.model.Pageable;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static io.kestra.core.utils.Rethrow.throwPredicate;
import static org.assertj.core.api.Assertions.assertThat;

@Singleton
public class LoopCaseTest {
    @Inject
    private TaskOutputService taskOutputService;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    public void loopSerial(Execution execution) throws InternalException {
        // Then
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(1);
        TaskRun loopTaskRun = execution.getTaskRunList().getFirst();
        assertThat(loopTaskRun.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(taskOutputService.getOutputs(loopTaskRun))
            .containsEntry(Loop.ITERATION_COUNT_OUTPUT, 3)
            .containsEntry(Loop.TERMINATED_ITERATIONS_OUTPUT, 3);

        // 3 loop sub-executions, one per iteration, all with SUCCESS
        List<Execution> subExecutions = loopSubExecutions(execution);
        assertThat(subExecutions).hasSize(3);
        assertThat(subExecutions).allMatch(sub -> sub.getState().getCurrent() == State.Type.SUCCESS);
        assertThat(subExecutions).allMatch(throwPredicate(sub -> {
            String expectedValue = sub.getLoopRun().index() + " - " + sub.getLoopRun().value();
            return expectedValue.equals(taskOutputService.getOutputs(sub.getTaskRunList().getFirst()).get("value"));
        }));
    }

    public void loopSerialMultipleTasks(Execution execution) throws InternalException {
        // Then
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(1);
        TaskRun loopTaskRun = execution.getTaskRunList().getFirst();
        assertThat(loopTaskRun.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(taskOutputService.getOutputs(loopTaskRun))
            .containsEntry(Loop.ITERATION_COUNT_OUTPUT, 2)
            .containsEntry(Loop.TERMINATED_ITERATIONS_OUTPUT, 2);

        // 2 loop sub-executions, one per iteration, each running 2 child tasks
        List<Execution> subExecutions = loopSubExecutions(execution);
        assertThat(subExecutions).hasSize(2);
        assertThat(subExecutions).allMatch(sub -> sub.getState().getCurrent() == State.Type.SUCCESS);
        assertThat(subExecutions).allMatch(sub -> sub.getTaskRunList().size() == 2);
    }

    public void loopFailed(Execution execution) {
        // Then — first failing iteration terminates the loop immediately with transmitFailed=true (default)
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);

        // Only one sub-execution ran before the loop was terminated
        List<Execution> subExecutions = loopSubExecutions(execution);
        assertThat(subExecutions).hasSize(1);
        assertThat(subExecutions.getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    public void loopTransmitFailedFalse(Execution execution) throws InternalException {
        // Then — all iterations run but failures are not propagated, loop ends with SUCCESS
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(1);
        TaskRun loopTaskRun = execution.getTaskRunList().getFirst();
        assertThat(loopTaskRun.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(taskOutputService.getOutputs(loopTaskRun))
            .containsEntry(Loop.ITERATION_COUNT_OUTPUT, 3)
            .containsEntry(Loop.TERMINATED_ITERATIONS_OUTPUT, 3);

        // All 3 sub-executions ran and each failed individually (failure not propagated to the loop)
        List<Execution> subExecutions = loopSubExecutions(execution);
        assertThat(subExecutions).hasSize(3);
        assertThat(subExecutions).allMatch(sub -> sub.getState().getCurrent() == State.Type.FAILED);
    }

    public void loopParallelUnlimited(Execution execution) throws InternalException {
        // Then — concurrencyLimit: 0 means all 3 iterations start in parallel
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(1);
        TaskRun loopTaskRun = execution.getTaskRunList().getFirst();
        assertThat(loopTaskRun.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(taskOutputService.getOutputs(loopTaskRun))
            .containsEntry(Loop.ITERATION_COUNT_OUTPUT, 3)
            .containsEntry(Loop.TERMINATED_ITERATIONS_OUTPUT, 3);

        List<Execution> subExecutions = loopSubExecutions(execution);
        assertThat(subExecutions).hasSize(3);
        assertThat(subExecutions).allMatch(sub -> sub.getState().getCurrent() == State.Type.SUCCESS);
        assertThat(subExecutions).allMatch(throwPredicate(sub -> {
            String expectedValue = sub.getLoopRun().index() + " - " + sub.getLoopRun().value();
            return expectedValue.equals(taskOutputService.getOutputs(sub.getTaskRunList().getFirst()).get("value"));
        }));
    }

    public void loopParallelEqual(Execution execution) throws InternalException {
        // Then — concurrencyLimit equals the number of iterations: all 3 start at once
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(1);
        TaskRun loopTaskRun = execution.getTaskRunList().getFirst();
        assertThat(loopTaskRun.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(taskOutputService.getOutputs(loopTaskRun))
            .containsEntry(Loop.ITERATION_COUNT_OUTPUT, 3)
            .containsEntry(Loop.TERMINATED_ITERATIONS_OUTPUT, 3);

        List<Execution> subExecutions = loopSubExecutions(execution);
        assertThat(subExecutions).hasSize(3);
        assertThat(subExecutions).allMatch(sub -> sub.getState().getCurrent() == State.Type.SUCCESS);
        assertThat(subExecutions).allMatch(throwPredicate(sub -> {
            String expectedValue = sub.getLoopRun().index() + " - " + sub.getLoopRun().value();
            return expectedValue.equals(taskOutputService.getOutputs(sub.getTaskRunList().getFirst()).get("value"));
        }));
    }

    public void loopParallelMore(Execution execution) throws InternalException {
        // Then — concurrencyLimit (5) exceeds the number of iterations (3): all 3 start at once
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(1);
        TaskRun loopTaskRun = execution.getTaskRunList().getFirst();
        assertThat(loopTaskRun.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(taskOutputService.getOutputs(loopTaskRun))
            .containsEntry(Loop.ITERATION_COUNT_OUTPUT, 3)
            .containsEntry(Loop.TERMINATED_ITERATIONS_OUTPUT, 3);

        List<Execution> subExecutions = loopSubExecutions(execution);
        assertThat(subExecutions).hasSize(3);
        assertThat(subExecutions).allMatch(sub -> sub.getState().getCurrent() == State.Type.SUCCESS);
        assertThat(subExecutions).allMatch(throwPredicate(sub -> {
            String expectedValue = sub.getLoopRun().index() + " - " + sub.getLoopRun().value();
            return expectedValue.equals(taskOutputService.getOutputs(sub.getTaskRunList().getFirst()).get("value"));
        }));
    }

    public void loopParallelLess(Execution execution) throws InternalException {
        // Then — concurrencyLimit (2) is less than the number of iterations (4): runs in batches
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(1);
        TaskRun loopTaskRun = execution.getTaskRunList().getFirst();
        assertThat(loopTaskRun.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(taskOutputService.getOutputs(loopTaskRun))
            .containsEntry(Loop.ITERATION_COUNT_OUTPUT, 4)
            .containsEntry(Loop.TERMINATED_ITERATIONS_OUTPUT, 4);

        List<Execution> subExecutions = loopSubExecutions(execution);
        assertThat(subExecutions).hasSize(4);
        assertThat(subExecutions).allMatch(sub -> sub.getState().getCurrent() == State.Type.SUCCESS);
        assertThat(subExecutions).allMatch(throwPredicate(sub -> {
            String expectedValue = sub.getLoopRun().index() + " - " + sub.getLoopRun().value();
            return expectedValue.equals(taskOutputService.getOutputs(sub.getTaskRunList().getFirst()).get("value"));
        }));
    }

    public void loopFlowable(Execution execution) throws InternalException {
        // Then
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(1);
        TaskRun loopTaskRun = execution.getTaskRunList().getFirst();
        assertThat(loopTaskRun.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(taskOutputService.getOutputs(loopTaskRun))
            .containsEntry(Loop.ITERATION_COUNT_OUTPUT, 3)
            .containsEntry(Loop.TERMINATED_ITERATIONS_OUTPUT, 3);

        // 3 loop sub-executions, one per iteration, all with SUCCESS
        List<Execution> subExecutions = loopSubExecutions(execution);
        assertThat(subExecutions).hasSize(3);
        assertThat(subExecutions).allMatch(sub -> sub.getState().getCurrent() == State.Type.SUCCESS);
    }

    public void loopMultiple(Execution execution) throws InternalException {
        // Then — flow has 2 sequential Loop tasks (loop1 + loop2), each with 3 values
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(2);
        for (TaskRun loopTaskRun : execution.getTaskRunList()) {
            assertThat(loopTaskRun.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
            assertThat(taskOutputService.getOutputs(loopTaskRun))
                .containsEntry(Loop.ITERATION_COUNT_OUTPUT, 3)
                .containsEntry(Loop.TERMINATED_ITERATIONS_OUTPUT, 3);
        }

        // 6 loop sub-executions total (3 per loop task), all with SUCCESS
        List<Execution> subExecutions = loopSubExecutions(execution);
        assertThat(subExecutions).hasSize(6);
        assertThat(subExecutions).allMatch(sub -> sub.getState().getCurrent() == State.Type.SUCCESS);
        assertThat(subExecutions).allMatch(throwPredicate(sub -> {
            String expectedValue = sub.getLoopRun().index() + " - " + sub.getLoopRun().value();
            return expectedValue.equals(taskOutputService.getOutputs(sub.getTaskRunList().getFirst()).get("value"));
        }));
    }

    public void loopNested(Execution execution) throws InternalException {
        // Then
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(1);
        TaskRun loopTaskRun = execution.getTaskRunList().getFirst();
        assertThat(loopTaskRun.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(taskOutputService.getOutputs(loopTaskRun))
            .containsEntry(Loop.ITERATION_COUNT_OUTPUT, 3)
            .containsEntry(Loop.TERMINATED_ITERATIONS_OUTPUT, 3);

        // 3 loop sub-executions, one per iteration, all with SUCCESS
        List<Execution> subExecutions = loopSubExecutions(execution);
        assertThat(subExecutions).hasSize(3);
        assertThat(subExecutions).allMatch(sub -> sub.getState().getCurrent() == State.Type.SUCCESS);
        assertThat(subExecutions).allMatch(throwPredicate(sub -> {
            TaskRun subLoopTaskRun = sub.getTaskRunList().getFirst();
            Map<String, Object> subLoopOutputs = taskOutputService.getOutputs(subLoopTaskRun);
            return subLoopTaskRun.getState().getCurrent() == State.Type.SUCCESS
                && Integer.valueOf(3).equals(subLoopOutputs.get(Loop.ITERATION_COUNT_OUTPUT))
                && Integer.valueOf(3).equals(subLoopOutputs.get(Loop.TERMINATED_ITERATIONS_OUTPUT));
        }));

        // each loop1 sub-execution should have 3 loop2 sub-executions, each running loop3
        for (Execution loop1SubExec : subExecutions) {
            List<Execution> loop2SubExecutions = loopSubExecutions(loop1SubExec);
            assertThat(loop2SubExecutions).hasSize(3);
            assertThat(loop2SubExecutions).allMatch(sub -> sub.getState().getCurrent() == State.Type.SUCCESS);
            assertThat(loop2SubExecutions).allMatch(throwPredicate(sub -> {
                TaskRun loop3TaskRun = sub.getTaskRunList().getFirst();
                Map<String, Object> loop3Outputs = taskOutputService.getOutputs(loop3TaskRun);
                return loop3TaskRun.getState().getCurrent() == State.Type.SUCCESS
                    && Integer.valueOf(3).equals(loop3Outputs.get(Loop.ITERATION_COUNT_OUTPUT))
                    && Integer.valueOf(3).equals(loop3Outputs.get(Loop.TERMINATED_ITERATIONS_OUTPUT));
            }));
            // each loop2 sub-execution should have 3 loop3 sub-executions with Return task outputs
            for (Execution loop2SubExec : loop2SubExecutions) {
                List<Execution> loop3SubExecutions = loopSubExecutions(loop2SubExec);
                assertThat(loop3SubExecutions).hasSize(3);
                assertThat(loop3SubExecutions).allMatch(sub -> sub.getState().getCurrent() == State.Type.SUCCESS);
                for (Execution loop3SubExec : loop3SubExecutions) {
                    LoopRun lr = loop3SubExec.getLoopRun();
                    assertThat(lr).isNotNull();
                    assertThat(lr.parents()).isNotNull();
                    LoopRun.Parent loop2Parent = lr.parents().getLast();
                    LoopRun.Parent loop1Parent = lr.parents().getFirst();

                    // item: "{{item.index}} - {{item.value}}"
                    TaskRun itemRun = loop3SubExec.getTaskRunList().stream().filter(tr -> "item".equals(tr.getTaskId())).findFirst().orElseThrow();
                    assertThat(taskOutputService.getOutputs(itemRun))
                        .containsEntry("value", lr.index() + " - " + lr.value());

                    // parent: "{{item.parent.index}} - {{item.parent.value}}" — resolves to the loop2 context
                    TaskRun parentRun = loop3SubExec.getTaskRunList().stream().filter(tr -> "parent".equals(tr.getTaskId())).findFirst().orElseThrow();
                    assertThat(taskOutputService.getOutputs(parentRun))
                        .containsEntry("value", loop2Parent.index() + " - " + loop2Parent.value());

                    // parents: "{{item.parents[0].index}} - {{item.parents[0].value}} - {{item.parents[1].index}} - {{item.parents[1].value}}"
                    // parents[0] = loop1 context, parents[1] = loop2 context
                    TaskRun parentsRun = loop3SubExec.getTaskRunList().stream().filter(tr -> "parents".equals(tr.getTaskId())).findFirst().orElseThrow();
                    assertThat(taskOutputService.getOutputs(parentsRun))
                        .containsEntry("value", loop1Parent.index() + " - " + loop1Parent.value() + " - " + loop2Parent.index() + " - " + loop2Parent.value());
                }
            }
        }
    }

    public void loopMap(Execution execution) throws InternalException {
        // Then — values defined as a map: each iteration exposes item.key and item.value
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(1);
        TaskRun loopTaskRun = execution.getTaskRunList().getFirst();
        assertThat(loopTaskRun.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(taskOutputService.getOutputs(loopTaskRun))
            .containsEntry(Loop.ITERATION_COUNT_OUTPUT, 3)
            .containsEntry(Loop.TERMINATED_ITERATIONS_OUTPUT, 3);

        // 3 loop sub-executions, one per map entry, all with SUCCESS
        List<Execution> subExecutions = loopSubExecutions(execution);
        assertThat(subExecutions).hasSize(3);
        assertThat(subExecutions).allMatch(sub -> sub.getState().getCurrent() == State.Type.SUCCESS);
        // each iteration must carry a non-null key
        assertThat(subExecutions).allMatch(sub -> sub.getLoopRun().key() != null);
        assertThat(subExecutions).allMatch(throwPredicate(sub -> {
            LoopRun lr = sub.getLoopRun();
            String expectedOutput = lr.key() + " - " + lr.value();
            return expectedOutput.equals(taskOutputService.getOutputs(sub.getTaskRunList().getFirst()).get("value"));
        }));
    }

    public void loopValuesFromUri(Execution execution) throws InternalException {
        // Then — values loaded from an ION file URI created by a preceding Write task
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(2);
        TaskRun loopTaskRun = execution.getTaskRunList().getLast();
        assertThat(loopTaskRun.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(taskOutputService.getOutputs(loopTaskRun))
            .containsEntry(Loop.ITERATION_COUNT_OUTPUT, 3)
            .containsEntry(Loop.TERMINATED_ITERATIONS_OUTPUT, 3);

        // 3 loop sub-executions, one per ION value, all with SUCCESS
        List<Execution> subExecutions = loopSubExecutions(execution);
        assertThat(subExecutions).hasSize(3);
        assertThat(subExecutions).allMatch(sub -> sub.getState().getCurrent() == State.Type.SUCCESS);
        assertThat(subExecutions).allMatch(throwPredicate(sub -> {
            String expectedValue = sub.getLoopRun().index() + " - " + sub.getLoopRun().value();
            return expectedValue.equals(taskOutputService.getOutputs(sub.getTaskRunList().getFirst()).get("value"));
        }));
    }

    /** Returns the loop sub-executions for the given parent execution, sorted by iteration index. */
    private List<Execution> loopSubExecutions(Execution parentExecution) {
        // FIXME retrieving loop sub-executions for a given loop should be more easy
        return executionRepository.findByFlowId(parentExecution.getTenantId(), parentExecution.getNamespace(), parentExecution.getFlowId(), Pageable.UNPAGED)
            .stream()
            .filter(exec -> parentExecution.getId().equals(exec.getParentId()))
            .sorted(Comparator.comparingInt(exec -> exec.getLoopRun().index()))
            .toList();
    }
}
