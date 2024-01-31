package io.kestra.core.tasks.flows;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.Label;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.ExecutableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.ExecutableUtils;
import io.kestra.core.runners.FlowExecutorInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.SubflowExecution;
import io.kestra.core.runners.SubflowExecutionResult;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.services.StorageService;
import io.kestra.core.storages.StorageSplitInterface;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static io.kestra.core.utils.Rethrow.throwFunction;

@SuperBuilder(toBuilder = true)
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute a subflow for each batch of items",
    description = """
        The `items` value must be Kestra's internal storage URI e.g. an output file from a previous task, or a file from inputs of FILE type.
        Two special variables are available to pass as inputs to the subflow:
        - `taskrun.items` which is the URI of internal storage file containing the batch of items to process
        - `taskrun.iteration` which is the iteration or batch number"""
)
@Plugin(
    examples = {
        @Example(
            title = """
                Execute a subflow for each batch of items. The subflow `orders` is called from the parent flow `orders_parallel` using the `ForEachItem` task in order to start one subflow execution for each batch of items.
                ```yaml
                id: orders
                namespace: prod

                inputs:
                  - id: order
                    type: STRING

                tasks:
                  - id: read_file
                    type: io.kestra.plugin.scripts.shell.Commands
                    runner: PROCESS
                    commands:
                      - cat "{{ inputs.order }}"

                  - id: read_file_content
                    type: io.kestra.core.tasks.log.Log
                    message: "{{ read(inputs.order) }}"
                ```
                """,
            full = true,
            code = """
                id: orders_parallel
                namespace: prod

                tasks:
                  - id: extract
                    type: io.kestra.plugin.jdbc.duckdb.Query
                    sql: |
                      INSTALL httpfs;
                      LOAD httpfs;
                      SELECT *
                      FROM read_csv_auto('https://raw.githubusercontent.com/kestra-io/datasets/main/csv/orders.csv', header=True);
                    store: true

                  - id: each
                    type: io.kestra.core.tasks.flows.ForEachItem
                    items: "{{ outputs.extract.uri }}"
                    batch:
                      rows: 1
                    namespace: prod
                    flowId: orders
                    wait: true # wait for the subflow execution
                    transmitFailed: true # fail the task run if the subflow execution fails
                    inputs:
                      order: "{{ taskrun.items }}" # special variable that contains the items of the batch
                """
        )
    }
)
public class ForEachItem extends Task implements ExecutableTask<ForEachItem.Output> {
    @NotEmpty
    @PluginProperty(dynamic = true)
    @Schema(title = "The items to be split into batches and processed. Make sure to set it to Kestra's internal storage URI. This can be either the output from a previous task, formatted as `{{ outputs.task_id.uri }}`, or a FILE type input parameter, like `{{ inputs.myfile }}`. This task is optimized for files where each line represents a single item. Suitable file types include Amazon ION-type files (commonly produced by Query tasks), newline-separated JSON files, or CSV files formatted with one row per line and without a header. For files in other formats such as Excel, CSV, Avro, Parquet, XML, or JSON, it's recommended to first convert them to the ION format. This can be done using the conversion tasks available in the `io.kestra.plugin.serdes` module, which will transform files from their original format to ION.")
    private String items;

    @NotNull
    @PluginProperty
    @Builder.Default
    @Schema(title = "How to split the items into batches.")
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
        title = "Whether to fail the current execution if the subflow execution fails or is killed.",
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
    public List<SubflowExecution<?>> createSubflowExecutions(
        RunContext runContext,
        FlowExecutorInterface flowExecutorInterface,
        Flow currentFlow,
        Execution currentExecution,
        TaskRun currentTaskRun
    ) throws InternalException {
        var renderedUri = runContext.render(this.items);
        if (!renderedUri.startsWith("kestra://")) {
            var errorMessage = "Unable to split the items from " + renderedUri + ", this is not an internal storage URI!";
            runContext.logger().error(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }

        try {
            List<URI> splits = StorageService.split(runContext, this.batch, URI.create(renderedUri));

            AtomicInteger currentIteration = new AtomicInteger(1);

            return splits.stream()
                .<SubflowExecution<?>>map(throwFunction(
                    split -> {
                        int iteration = currentIteration.getAndIncrement();
                        // these are special variable that can be passed to the subflow
                        Map<String, Object> itemsVariable = Map.of("taskrun",
                            Map.of("items", split.toString(), "iteration", iteration));
                        Map<String, Object> inputs = new HashMap<>();
                        if (this.inputs != null) {
                            inputs.putAll(runContext.render(this.inputs, itemsVariable));
                        }

                        List<Label> labels = new ArrayList<>();
                        if (this.inheritLabels && currentExecution.getLabels() != null && !currentExecution.getLabels().isEmpty()) {
                            labels.addAll(currentExecution.getLabels());
                        }

                        if (this.labels != null) {
                            for (Map.Entry<String, String> entry : this.labels.entrySet()) {
                                labels.add(new Label(entry.getKey(), runContext.render(entry.getValue())));
                            }
                        }

                        // these are special outputs to be able to compute iteration map of the parent taskrun
                        var outputs = Output.builder()
                            .numberOfBatches(splits.size())
                            // the passed URI may be used by the subflow to write execution outputs.
                            .uri(URI.create(runContext.getStorageOutputPrefix().toString() + "/" + iteration + "/outputs.ion"))
                            .build();
                        return ExecutableUtils.subflowExecution(
                            runContext,
                            flowExecutorInterface,
                            currentExecution,
                            currentFlow,
                            this,
                            currentTaskRun
                                .withOutputs(outputs.toMap())
                                .withIteration(iteration),
                            inputs,
                            labels
                        );
                    }
                ))
                .toList();
        } catch (IOException e) {
            runContext.logger().error(e.getMessage(), e);
            throw new InternalException(e);
        }
    }

    @Override
    public Optional<SubflowExecutionResult> createSubflowExecutionResult(
        RunContext runContext,
        TaskRun taskRun,
        Flow flow,
        Execution execution
    ) {

        // We only resolve subflow outputs for an execution result when the execution is terminated.
        if (taskRun.getState().isTerminated() && flow.getOutputs() != null && waitForExecution()) {
            final Map<String, Object> outputs = flow.getOutputs()
                .stream()
                .collect(Collectors.toMap(
                    io.kestra.core.models.flows.Output::getId,
                    io.kestra.core.models.flows.Output::getValue)
                );
            final ForEachItem.Output.OutputBuilder builder = Output
                .builder()
                .iterations((Map<State.Type, Integer>) taskRun.getOutputs().get("iterations"))
                .numberOfBatches((Integer) taskRun.getOutputs().get("numberOfBatches"));

            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                FileSerde.write(bos, runContext.render(outputs));
                URI uri = runContext.storage().putFile(
                    new ByteArrayInputStream(bos.toByteArray()),
                    URI.create((String) taskRun.getOutputs().get("uri"))
                );
                builder.uri(uri);
            } catch (Exception e) {
                runContext.logger().warn("Failed to extract outputs with the error: '{}'", e.getLocalizedMessage(), e);
                var state = this.isAllowFailure() ? State.Type.WARNING : State.Type.FAILED;
                taskRun = taskRun
                    .withState(state)
                    .withAttempts(Collections.singletonList(TaskRunAttempt.builder().state(new State().withState(state)).build()))
                    .withOutputs(builder.build().toMap());

                return Optional.of(SubflowExecutionResult.builder()
                    .executionId(execution.getId())
                    .state(State.Type.FAILED)
                    .parentTaskRun(taskRun)
                    .build());
            }
            taskRun = taskRun.withOutputs(builder.build().toMap());
        }

        // ForEachItem is an iterative task, the terminal state will be computed in the executor while counting on the task run execution list
        return Optional.of(ExecutableUtils.subflowExecutionResult(taskRun, execution));
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
            title = "The counter of iterations for each subflow execution state.",
            description = "This output will be updated in real-time based on the state of subflow executions.\n It will contain one counter by subflow execution state."
        )
        private final Map<State.Type, Integer> iterations;

        @Schema(
            title = "The number of batches."
        )
        private final Integer numberOfBatches;

        @Schema(
            title = "The URI of the file gathering outputs from each subflow execution."
        )
        private final URI uri;
    }
}
