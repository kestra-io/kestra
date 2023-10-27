package io.kestra.core.tasks.flows;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.Label;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.tasks.ExecutableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.ExecutableUtils;
import io.kestra.core.runners.FlowExecutorInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.runners.WorkerTaskExecution;
import io.kestra.core.runners.WorkerTaskResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

import static io.kestra.core.utils.Rethrow.throwFunction;

@SuperBuilder(toBuilder = true)
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute a subflow for each batch of items",
    description = "This tasks allow to execute a subflow for each batch of items. The items must come from Kestra's internal storage."
)
@Plugin(
    examples = {
        @Example(
            title = "Execute a subflow for each batch of items",
            code = {
                """
                    id: each
                    type: io.kestra.core.tasks.flows.ForEachItem
                    items: "{{ outputs.extract.uri }}" # works with API payloads too. Kestra can detect if this output is not a file,\s
                    # and will make it to a file, split into (batches of) items
                    maxItemsPerBatch: 10
                    subflow:
                      flowId: file
                      namespace: dev
                      inputs:
                        file: "{{ taskrun.items }}" # special variable that contains the items of the batch
                      wait: true # wait for the subflow execution
                      transmitFailed: true # fail the task run if the subflow fail"""
            }
        )
    }
)
public class ForEachItem extends Task implements ExecutableTask {
    private static final String URI_FORMAT = "kestra:///%s/%s/executions/%s/tasks/%s/%s/batch-%s.ion";

    @NotEmpty
    @PluginProperty(dynamic = true)
    @Schema(title = "The items, must be an URI from Kestra's internal storage")
    private String items;

    @Positive
    @NotNull
    @PluginProperty
    @Builder.Default
    @Schema(title = "Maximum number of items per batch")
    private Integer maxItemsPerBatch = 10;

    @NotNull
    @PluginProperty
    @Schema(title = "The subflow that will be executed on each batch of items")
    private ForEachItem.Subflow subflow;

    @Override
    public List<WorkerTaskExecution<?>> createWorkerTaskExecutions(RunContext runContext,
                                            FlowExecutorInterface flowExecutorInterface,
                                            Flow currentFlow,
                                            Execution currentExecution,
                                            TaskRun currentTaskRun) throws InternalException {
        int splits = readSplits(runContext);

        return IntStream.range(1, splits + 1).boxed()
            .<WorkerTaskExecution<?>>map(throwFunction(
                 split -> {
                    //FIXME duplicated with the flow task
                    RunnerUtils runnerUtils = runContext.getApplicationContext().getBean(RunnerUtils.class);

                    Map<String, Object> inputs = new HashMap<>();
                    if (this.subflow.inputs != null) {
                        inputs.putAll(runContext.render(this.subflow.inputs));
                    }

                    List<Label> labels = new ArrayList<>();
                    if (this.subflow.inheritLabels) {
                        labels.addAll(currentExecution.getLabels());
                    }
                    if (this.subflow.labels != null) {
                        for (Map.Entry<String, String> entry: this.subflow.labels.entrySet()) {
                            labels.add(new Label(entry.getKey(), runContext.render(entry.getValue())));
                        }
                    }

                    String namespace = runContext.render(this.subflow.namespace);
                    String flowId = runContext.render(this.subflow.flowId);
                    Optional<Integer> revision = this.subflow.revision != null ? Optional.of(this.subflow.revision) : Optional.empty();

                    io.kestra.core.models.flows.Flow flow = flowExecutorInterface.findByIdFromFlowTask(
                            currentExecution.getTenantId(),
                            namespace,
                            flowId,
                            revision,
                            currentExecution.getTenantId(),
                            currentFlow.getNamespace(),
                            currentFlow.getId()
                        )
                        .orElseThrow(() -> new IllegalStateException("Unable to find flow '" + namespace + "'.'" + flowId + "' with revision + '" + revision + "'"));

                    if (flow.isDisabled()) {
                        throw new IllegalStateException("Cannot execute disabled flow");
                    }

                    if (flow instanceof FlowWithException fwe) {
                        throw new IllegalStateException("Cannot execute an invalid flow: " + fwe.getException());
                    }

                    Execution execution = runnerUtils
                        .newExecution(
                            flow,
                            (f, e) -> runnerUtils.typedInputs(f, e, inputs),
                            labels)
                        .withTrigger(ExecutionTrigger.builder()
                            .id(this.getId())
                            .type(this.getType())
                            .variables(ImmutableMap.of(
                                "executionId", currentExecution.getId(),
                                "namespace", currentFlow.getNamespace(),
                                "flowId", currentFlow.getId(),
                                "flowRevision", currentFlow.getRevision(),
                                "items", readItems(currentExecution, currentTaskRun.getId(), split)
                            ))
                            .build()
                        );

                     return WorkerTaskExecution.builder()
                         .task(this)
                         .taskRun(currentTaskRun
                             .withValue(String.valueOf(split))
                             .withOutputs(ImmutableMap.of(
                                 "currentIteration", split,
                                 "maxIterations", splits
                             )))
                         .execution(execution)
                         .build();
                }
            ))
            .toList();
    }

    @Override
    public Optional<WorkerTaskResult> createWorkerTaskResult(
        RunContext runContext,
        WorkerTaskExecution<?> workerTaskExecution,
        Flow flow,
        Execution execution
    ) {
        TaskRun taskRun = workerTaskExecution.getTaskRun();

        taskRun = taskRun.withState(ExecutableUtils.guessState(execution, this.subflow.transmitFailed));

        int currentIteration = (Integer) taskRun.getOutputs().get("currentIteration");
        int maxIterations = (Integer) taskRun.getOutputs().get("maxIterations");

        return currentIteration == maxIterations ? Optional.of(ExecutableUtils.workerTaskResult(taskRun)) : Optional.empty();
    }

    @Override
    public boolean waitForExecution() {
        return this.subflow.wait;
    }

    private URI readItems(Execution execution, String taskRunId, int split) {
        // Recreate the URI from the execution context and the value.
        // It should be kestra:///$ns/$flow/executions/$execution_id/tasks/$task_id/$taskrun_id/bach-$value.ion
        String uri = URI_FORMAT.formatted(execution.getNamespace(), execution.getFlowId(), execution.getId(), this.id, taskRunId, split);
        return URI.create(uri);
    }

    private int readSplits(RunContext runContext) throws IllegalVariableEvaluationException {
        URI data = URI.create(runContext.render(this.items));

        try (var reader = new BufferedReader(new InputStreamReader(runContext.uriToInputStream(data)))) {
            int batches = 0;
            int lineNb = 0;
            String row;
            List<String> rows = new ArrayList<>(maxItemsPerBatch);
            while ((row = reader.readLine()) != null) {
                rows.add(row);
                lineNb++;

                if (lineNb == maxItemsPerBatch) {
                    createBatchFile(runContext, rows, batches);

                    batches++;
                    lineNb = 0;
                    rows.clear();
                }
            }

            if (!rows.isEmpty()) {
                createBatchFile(runContext, rows, batches);
                batches++;
            }

            return batches;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void createBatchFile(RunContext runContext, List<String> rows, int batch) throws IOException {
        byte[] bytes = rows.stream().collect(Collectors.joining(System.lineSeparator())).getBytes();
        File batchFile = runContext.tempFile(bytes, ".ion").toFile();
        runContext.putTempFile(batchFile, "batch-" + (batch + 1) + ".ion");
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class Subflow {
        @NotEmpty
        @Schema(
            title = "The namespace of the flow to trigger"
        )
        @PluginProperty(dynamic = true)
        private String namespace;

        @NotEmpty
        @Schema(
            title = "The identifier of the flow to trigger"
        )
        @PluginProperty(dynamic = true)
        private String flowId;

        @Schema(
            title = "The revision of the flow to trigger",
            description = "By default, we trigger the last version."
        )
        @PluginProperty
        private Integer revision;

        @Schema(
            title = "The inputs to pass to the triggered flow"
        )
        @PluginProperty(dynamic = true)
        private Map<String, Object> inputs;

        @Schema(
            title = "The labels to pass to the triggered flow execution"
        )
        @PluginProperty(dynamic = true)
        private Map<String, String> labels;

        @Builder.Default
        @Schema(
            title = "Wait the end of the execution."
        )
        @PluginProperty
        private final Boolean wait = true;

        @Builder.Default
        @Schema(
            title = "Fail the current execution if the waited execution is failed or killed.",
            description = "`wait` need to be true to make it work"
        )
        @PluginProperty
        private final Boolean transmitFailed = true;

        @Builder.Default
        @Schema(
            title = "Inherit labels from the calling execution",
            description = "By default, we don't inherit any labels of the calling execution"
        )
        @PluginProperty
        private final Boolean inheritLabels = false;
    }
}
