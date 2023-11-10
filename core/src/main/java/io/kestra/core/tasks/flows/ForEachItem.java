package io.kestra.core.tasks.flows;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.Label;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.tasks.ExecutableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.ExecutableUtils;
import io.kestra.core.runners.FlowExecutorInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.WorkerTaskExecution;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.services.StorageService;
import io.kestra.core.storages.StorageSplitInterface;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import static io.kestra.core.utils.Rethrow.throwFunction;

@SuperBuilder(toBuilder = true)
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute a subflow for each batch of items",
    description = "Execute a subflow for each batch of items. The `items` value must be internal storage URI e.g. an output file from a previous task, or a file from inputs of FILE type."
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
                    batch:
                      rows: 10
                    flowId: file
                    namespace: dev
                    inputs:
                      file: "{{ taskrun.items }}" # special variable that contains the items of the batch
                    wait: true # wait for the subflow execution
                    transmitFailed: true # fail the task run if the subflow execution fails"""
            }
        )
    }
)
public class ForEachItem extends Task implements ExecutableTask<ForEachItem.Output> {
    @NotEmpty
    @PluginProperty(dynamic = true)
    @Schema(title = "The items to be split into batches and processed. Make sure to set it to Kestra's internal storage URI, e.g. output from a previous task in the format `{{ outputs.task_id.uri }}` or an input parameter of FILE type e.g. `{{ inputs.myfile }}`.")
    private String items;

    @NotNull
    @PluginProperty
    @Builder.Default
    @Schema(title = "The batch split size")
    private ForEachItem.Batch batch = Batch.builder().build();

    @NotEmpty
    @Schema(
        title = "The namespace of the subflow to be executed"
    )
    @PluginProperty(dynamic = true)
    private String namespace;

    @NotEmpty
    @Schema(
        title = "The identifier of the subflow to be executed"
    )
    @PluginProperty(dynamic = true)
    private String flowId;

    @Schema(
        title = "The revision of the subflow to be executed",
        description = "By default, the last, i.e. the most recent, revision of the subflow is executed."
    )
    @PluginProperty
    private Integer revision;

    @Schema(
        title = "The inputs to pass to the subflow to be executed"
    )
    @PluginProperty(dynamic = true)
    private Map<String, Object> inputs;

    @Schema(
        title = "The labels to pass to the subflow to be executed"
    )
    @PluginProperty(dynamic = true)
    private Map<String, String> labels;

    @Builder.Default
    @Schema(
        title = "Whether to wait for the subflows execution to finish before continuing the current execution."
    )
    @PluginProperty
    private final Boolean wait = true;

    @Builder.Default
    @Schema(
        title = "Whether to fail the current execution if the subflow execution fails or is killed",
        description = "Note that this option works only if `wait` is set to `true`."
    )
    @PluginProperty
    private final Boolean transmitFailed = true;

    @Builder.Default
    @Schema(
        title = "Whether the subflow should inherit labels from this execution that triggered it.",
        description = "By default, labels are not passed to the subflow execution. If you set this option to `true`, the child flow execution will inherit all labels from the parent execution."
    )
    @PluginProperty
    private final Boolean inheritLabels = false;

    @Override
    public List<WorkerTaskExecution<?>> createWorkerTaskExecutions(
        RunContext runContext,
        FlowExecutorInterface flowExecutorInterface,
        Flow currentFlow,
        Execution currentExecution,
        TaskRun currentTaskRun
    ) throws InternalException {
        try {
            List<URI> splits = StorageService.split(runContext, this.batch, URI.create(runContext.render(this.items)));

            AtomicInteger currentIteration = new AtomicInteger(1);

            return splits.stream()
                .<WorkerTaskExecution<?>>map(throwFunction(
                    split -> {
                        Map<String, Object> itemsVariable = Map.of("taskrun", Map.of("items", split.toString()));
                        Map<String, Object> inputs = new HashMap<>();
                        if (this.inputs != null) {
                            inputs.putAll(runContext.render(this.inputs, itemsVariable));
                        }

                        List<Label> labels = new ArrayList<>();
                        if (this.inheritLabels) {
                            labels.addAll(currentExecution.getLabels());
                        }
                        if (this.labels != null) {
                            for (Map.Entry<String, String> entry: this.labels.entrySet()) {
                                labels.add(new Label(entry.getKey(), runContext.render(entry.getValue())));
                            }
                        }

                        int iteration = currentIteration.getAndIncrement();
                        var outputs = Output.builder().iterations(Map.of("max", splits.size())).build();
                        return ExecutableUtils.workerTaskExecution(
                            runContext,
                            flowExecutorInterface,
                            currentExecution,
                            currentFlow,
                            this,
                            currentTaskRun
                                .withOutputs(outputs.toMap())
                                .withItems(split.toString()),
                            inputs,
                            labels,
                            iteration
                        );
                    }
                ))
                .toList();
        } catch (IOException e) {
            throw new InternalException(e);
        }
    }

    @Override
    public Optional<WorkerTaskResult> createWorkerTaskResult(
        RunContext runContext,
        TaskRun taskRun,
        Flow flow,
        Execution execution
    ) {
        // ForEachItem is an iterative task, the terminal state will be computed in the executor while counting on the task run execution list
        return Optional.of(ExecutableUtils.workerTaskResult(taskRun));
    }

    @Override
    public boolean waitForExecution() {
        return this.wait;
    }

    @Override
    public SubflowId subflowId() {
        return new SubflowId(namespace, flowId, Optional.ofNullable(revision));
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class Batch implements StorageSplitInterface {
        private String bytes;

        private Integer partitions;

        @Builder.Default
        private Integer rows = 1;

        @Builder.Default
        private String separator = "\n";
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The iterations counter.",
            description = "This output will be updated in real-time with the subflow executions.\n It will contains one counter by subflow execution state, plus a `max` counter that represent the maximum number of iterations (or the number of batches)."
        )
        private final Map<String, Integer> iterations;
    }
}
