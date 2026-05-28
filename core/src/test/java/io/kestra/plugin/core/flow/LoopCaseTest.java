package io.kestra.plugin.core.flow;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.*;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.TaskOutputService;
import io.kestra.core.storages.StorageInterface;

import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.event.Level;

import static io.kestra.core.utils.Await.await;
import static io.kestra.core.utils.Rethrow.throwPredicate;
import static org.assertj.core.api.Assertions.assertThat;

@Singleton
public class LoopCaseTest {
    private static final TypeReference<List<Map<String, Object>>> TYPE_REFERENCE = new TypeReference<>() {
    };

    @Inject
    private TaskOutputService taskOutputService;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private StorageInterface storageInterface;

    @Inject
    private LogRepositoryInterface logRepositoryInterface;

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
        List<Execution> subExecutions = executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId());
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
        List<Execution> subExecutions = executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId());
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
        List<Execution> subExecutions = executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId());
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
        List<Execution> subExecutions = executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId());
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

        List<Execution> subExecutions = executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId());
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

        List<Execution> subExecutions = executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId());
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

        List<Execution> subExecutions = executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId());
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

        List<Execution> subExecutions = executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId());
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
        List<Execution> subExecutions = executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId());
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
        List<Execution> subExecutions = executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId());
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
        List<Execution> subExecutions = executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId());
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
            List<Execution> loop2SubExecutions = executionRepository.findLoopSubExecutions(loop1SubExec.getTenantId(), loop1SubExec.getId());
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
                List<Execution> loop3SubExecutions = executionRepository.findLoopSubExecutions(loop2SubExec.getTenantId(), loop2SubExec.getId());
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
        List<Execution> subExecutions = executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId());
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
        List<Execution> subExecutions = executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId());
        assertThat(subExecutions).hasSize(3);
        assertThat(subExecutions).allMatch(sub -> sub.getState().getCurrent() == State.Type.SUCCESS);
        assertThat(subExecutions).allMatch(throwPredicate(sub -> {
            String expectedValue = sub.getLoopRun().index() + " - " + sub.getLoopRun().value();
            return expectedValue.equals(taskOutputService.getOutputs(sub.getTaskRunList().getFirst()).get("value"));
        }));
    }

    public void loopExpressionContext(Execution execution) throws InternalException {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(2);

        // 3 loop sub-executions, all with SUCCESS
        List<Execution> subExecutions = executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId());
        assertThat(subExecutions).hasSize(3);
        assertThat(subExecutions).allMatch(sub -> sub.getState().getCurrent() == State.Type.SUCCESS);
        assertThat(subExecutions).allMatch(sub -> sub.getTaskRunList().size() == 2);
        // each iteration must access the parent execution context from expressions
        assertThat(subExecutions).allMatch(throwPredicate(sub -> "I'm before the loop".equals(taskOutputService.getOutputs(sub.getTaskRunList().getFirst()).get("value"))));
        assertThat(subExecutions).allMatch(throwPredicate(sub -> execution.getId().equals(taskOutputService.getOutputs(sub.getTaskRunList().getLast()).get("value"))));
    }

    public void loopOutputs(Execution execution) throws InternalException, JsonProcessingException {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(2);

        // 3 loop sub-executions, all with SUCCESS
        List<Execution> subExecutions = executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId());
        assertThat(subExecutions).hasSize(3);
        assertThat(subExecutions).allMatch(sub -> sub.getState().getCurrent() == State.Type.SUCCESS);
        assertThat(subExecutions).allMatch(sub -> sub.getTaskRunList().size() == 1);

        // the last task can access loop iteration outputs
        var loopOutputs = taskOutputService.getOutputs(execution.getTaskRunList().getLast());
        var valueList = JacksonMapper.ofJson().readValue((String) loopOutputs.get("value"), TYPE_REFERENCE);
        assertThat(valueList).hasSize(3); // one output per iteration
        @SuppressWarnings("unchecked")
        var firstIterationMap = (Map<String, Object>)valueList.getFirst().get("outputs");
        assertThat(firstIterationMap).containsEntry("value", "some output");
    }

    public void loopOutputsStore(Execution execution) throws InternalException, IOException {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(2);

        // 3 loop sub-executions, all with SUCCESS
        List<Execution> subExecutions = executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId());
        assertThat(subExecutions).hasSize(3);
        assertThat(subExecutions).allMatch(sub -> sub.getState().getCurrent() == State.Type.SUCCESS);
        assertThat(subExecutions).allMatch(sub -> sub.getTaskRunList().size() == 1);

        // the loop task outputs should contain a URI per iteration
        TaskRun loopTaskRun = execution.getTaskRunList().getFirst();
        Map<String, Object> loopTaskOutputs = taskOutputService.getOutputs(loopTaskRun);
        assertThat(loopTaskOutputs)
            .containsEntry(Loop.ITERATION_COUNT_OUTPUT, 3)
            .containsEntry(Loop.RUNNING_ITERATIONS_OUTPUT, 0)
            .containsEntry(Loop.TERMINATED_ITERATIONS_OUTPUT, 3);

        @SuppressWarnings("unchecked")
        var valueList = (List<Map<String, Object>>) loopTaskOutputs.get(Loop.OUTPUTS_OUTPUT);
        assertThat(valueList).hasSize(3); // one output per iteration

        // each iteration output must carry a URI pointing to an ION file with the rendered output value
        for (Map<String, Object> output : valueList) {
            assertThat(output).extracting("item.value").isIn("value 1", "value 2", "value 3");
            @SuppressWarnings("unchecked")
            Map<String, Object> iterOutput = (Map<String, Object>) output.get("outputs");
            assertThat(iterOutput).containsKey("uri");
            String uri = (String) iterOutput.get("uri");
            assertThat(uri).startsWith("kestra://");

            // read stored ION content and verify the rendered output value
            try (InputStream content = storageInterface.get(execution.getTenantId(), execution.getNamespace(), URI.create(uri))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> stored = JacksonMapper.ofIon().readValue(content, Map.class);
                assertThat(stored).containsEntry("value", "some output");
            }
        }
    }

    public void loopOutputsFailedRender(Execution execution) {
        // Then — the loop output expression references a non-existent task, causing render to fail.
        // The LoopExecutionEvent carries FAILED state and, with transmitFailed=true (default),
        // immediately terminates the loop and propagates the failure to the parent execution.
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);

        // Only one sub-execution ran before the loop was terminated (transmitFailed=true by default).
        // The sub-execution itself is FAILED (output rendering failed), but its inner tasks succeeded.
        List<Execution> subExecutions = executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId());
        assertThat(subExecutions).hasSize(1);
        Execution subExecution = subExecutions.getFirst();
        assertThat(subExecution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(subExecution.getTaskRunList()).allMatch(tr -> tr.getState().getCurrent() == State.Type.SUCCESS);
    }

    public void loopOutputsAuto(Execution execution) throws InternalException, IOException {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(3);

        // 3 loop sub-executions, all with SUCCESS
        List<Execution> subExecutions = executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId());
        assertThat(subExecutions).hasSize(3);
        assertThat(subExecutions).allMatch(sub -> sub.getState().getCurrent() == State.Type.SUCCESS);
        assertThat(subExecutions).allMatch(sub -> sub.getTaskRunList().size() == 1);

        // the loop task outputs should contain a URI per iteration
        TaskRun loopTaskRun = execution.getTaskRunList().get(1);
        Map<String, Object> loopTaskOutputs = taskOutputService.getOutputs(loopTaskRun);
        assertThat(loopTaskOutputs)
            .containsEntry(Loop.ITERATION_COUNT_OUTPUT, 3)
            .containsEntry(Loop.RUNNING_ITERATIONS_OUTPUT, 0)
            .containsEntry(Loop.TERMINATED_ITERATIONS_OUTPUT, 3);

        @SuppressWarnings("unchecked")
        var valueList = (List<Map<String, Object>>) loopTaskOutputs.get(Loop.OUTPUTS_OUTPUT);
        assertThat(valueList).hasSize(3); // one output per iteration

        // each iteration output must carry a URI pointing to an ION file with the rendered output value
        for (Map<String, Object> entry : valueList) {
            @SuppressWarnings("unchecked")
            Map<String, Object> iterOutput = (Map<String, Object>) entry.get("outputs");
            assertThat(iterOutput).containsKey("uri");
            String uri = (String) iterOutput.get("uri");
            assertThat(uri).startsWith("kestra://");

            // read stored ION content and verify the rendered output value
            try (InputStream content = storageInterface.get(execution.getTenantId(), execution.getNamespace(), URI.create(uri))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> stored = JacksonMapper.ofIon().readValue(content, Map.class);
                assertThat(stored).containsEntry("value", "some output");
            }
        }
    }

    public void loopEmpty(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    public void loopWithNull(Execution  execution) {
        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var logs = logRepositoryInterface.findByExecutionId(execution.getTenantId(), execution.getId(), Level.INFO);
            assertThat(logs).isNotEmpty();
            assertThat(logs.stream().anyMatch(logEntry -> logEntry.getMessage().contains("Found a null value inside the iteration values"))).isTrue();
        });
    }

    public void loopObject(Execution execution) throws InternalException {
        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        List<Execution> subExecutions = executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId());
        assertThat(subExecutions).hasSize(3);
        assertThat((String) taskOutputService.getOutputs(subExecutions.get(2).getTaskRunList().get(1)).get("value")).contains("json > JSON > [\"my-complex\"]");
    }

    public void loopObjectInList(Execution execution) throws InternalException {
        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        List<Execution> subExecutions = executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId());
        assertThat(subExecutions).hasSize(3);
        assertThat((String) taskOutputService.getOutputs(subExecutions.get(2).getTaskRunList().get(1)).get("value")).contains("json > JSON > [\"my-complex\"]");
    }

    public void loopSwitch(Execution execution) throws InternalException {
        assertThat(execution.getTaskRunList()).hasSize(3);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        List<Execution> subExecutions = executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId());
        assertThat(subExecutions).hasSize(2);

        List<Execution> subSubExecutions = executionRepository.findLoopSubExecutions(subExecutions.get(1).getTenantId(), subExecutions.get(1).getId());
        assertThat(subSubExecutions).hasSize(2);
        TaskRun switchNumber1 = subSubExecutions.getFirst().findTaskRunsByTaskId("2-1-1_switch-number-1").getFirst();
        assertThat((String) taskOutputService.getOutputs(switchNumber1).get("value")).isEqualTo("1");

        subSubExecutions = executionRepository.findLoopSubExecutions(subExecutions.get(1).getTenantId(), subExecutions.get(1).getId());
        assertThat(subSubExecutions).hasSize(2);
        TaskRun switchNumber2 = subSubExecutions.get(1).findTaskRunsByTaskId("2-1-1_switch-number-2").getFirst();
        assertThat((String) taskOutputService.getOutputs(switchNumber2).get("value")).isEqualTo("2 b");
    }

    public void loopWithSubflow(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        var subExecutions = executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId());
        assertThat(subExecutions).hasSize(2);

        // for each sub-execution, check their subflow
        var subflowExecution1 = findSubflowExecution(subExecutions.getFirst());
        assertThat(subflowExecution1.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(subflowExecution1.getTaskRunList()).hasSize(1);
        var subflowExecution2 = findSubflowExecution(subExecutions.get(1));
        assertThat(subflowExecution2.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(subflowExecution2.getTaskRunList()).hasSize(1);
    }

    private Execution findSubflowExecution(Execution parent) {
        return  executionRepository.find(
            Pageable.UNPAGED,
            parent.getTenantId(),
            List.of(
                QueryFilter.builder()
                    .field(QueryFilter.Field.TRIGGER_EXECUTION_ID)
                    .operation(QueryFilter.Op.EQUALS)
                    .value(parent.getId())
                    .build()
            )
        ).getFirst();
    }
}
