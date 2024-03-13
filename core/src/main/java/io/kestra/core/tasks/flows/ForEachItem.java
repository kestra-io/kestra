package io.kestra.core.tasks.flows;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.Label;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.models.hierarchies.RelationType;
import io.kestra.core.models.tasks.*;
import io.kestra.core.runners.*;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.services.StorageService;
import io.kestra.core.storages.StorageSplitInterface;
import io.kestra.core.utils.GraphUtils;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
        ),
        @Example(
            title = """
                Execute a subflow for each JSON item fetched from a REST API. The subflow `mysubflow` is called from the parent flow `iterate_over_json` using the `ForEachItem` task; this creates one subflow execution for each JSON object.

                Note how we first need to convert the JSON array to JSON-L format using the `JsonWriter` task. This is because the `items` attribute of the `ForEachItem` task expects a file where each line represents a single item. Suitable file types include Amazon ION (commonly produced by Query tasks), newline-separated JSON files, or CSV files formatted with one row per line and without a header. For other formats, you can use the conversion tasks available in the `io.kestra.plugin.serdes` module.

                In this example, the subflow `mysubflow` expects a JSON object as input. The `JsonReader` task first reads the JSON array from the REST API and converts it to ION. Then, the `JsonWriter` task converts that ION file to JSON-L format, suitable for the `ForEachItem` task.

                ```yaml
                id: mysubflow
                namespace: dev

                inputs:
                  - id: json
                    type: JSON

                tasks:
                  - id: debug
                    type: io.kestra.core.tasks.log.Log
                    message: "{{ inputs.json }}"
                ```
                """,
            full = true,
            code = """
                id: iterate_over_json
                namespace: dev

                tasks:
                  - id: download
                    type: io.kestra.plugin.fs.http.Download
                    uri: "https://api.restful-api.dev/objects"
                    contentType: application/json
                    method: GET
                    failOnEmptyResponse: true
                    timeout: PT15S

                  - id: json_to_ion
                    type: io.kestra.plugin.serdes.json.JsonReader
                    from: "{{ outputs.download.uri }}"
                    newLine: false # regular json

                  - id: ion_to_jsonl
                    type: io.kestra.plugin.serdes.json.JsonWriter
                    from: "{{ outputs.json_to_ion.uri }}"
                    newLine: true # JSON-L

                  - id: for_each_item
                    type: io.kestra.core.tasks.flows.ForEachItem
                    items: "{{ outputs.ion_to_jsonl.uri }}"
                    batch:
                      rows: 1
                    namespace: dev
                    flowId: mysubflow
                    wait: true
                    transmitFailed: true
                    inputs:
                      json: '{{ json(read(taskrun.items)) }}'"""
        ),
        @Example(
            title = """
                This example shows how to use the combination of `EachSequential` and `ForEachItem` tasks to process files from an S3 bucket. The `EachSequential` iterates over files from the S3 trigger, and the `ForEachItem` task is used to split each file into batches. The `process_batch` subflow is then called with the `data` input parameter set to the URI of the batch to process.

                ```yaml
                id: process_batch
                namespace: dev

                inputs:
                  - id: data
                    type: FILE

                tasks:
                  - id: debug
                    type: io.kestra.core.tasks.log.Log
                    message: "{{ read(inputs.data) }}"
                ```
                """,
            full = true,
            code = """
                id: process_files
                namespace: dev

                tasks:
                  - id: loop_over_files
                    type: io.kestra.core.tasks.flows.EachSequential
                    value: "{{ trigger.objects | jq('.[].uri') }}"
                    tasks:
                      - id: subflow_per_batch
                        type: io.kestra.core.tasks.flows.ForEachItem
                        items: "{{ trigger.uris[parent.taskrun.value] }}" 
                        batch:
                          rows: 1
                        flowId: process_batch
                        namespace: dev
                        wait: true
                        transmitFailed: true
                        inputs:
                          data: "{{ taskrun.items }}"

                triggers:
                  - id: s3
                    type: io.kestra.plugin.aws.s3.Trigger
                    interval: "PT1S"
                    accessKeyId: "<access-key>"
                    secretKeyId: "<secret-key>"
                    region: "us-east-1"
                    bucket: "my_bucket"
                    prefix: "sub-dir"
                    action: NONE"""
        )
    }
)
public class ForEachItem extends Task implements FlowableTask<VoidOutput>, ChildFlowInterface {
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

    @Valid
    private List<Task> errors;

    @Override
    public GraphCluster tasksTree(Execution execution, TaskRun taskRun, List<String> parentValues) throws IllegalVariableEvaluationException {
        GraphCluster subGraph = new GraphCluster(this, taskRun, parentValues, RelationType.SEQUENTIAL);

        GraphUtils.sequential(
            subGraph,
            this.getTasks(),
            this.errors,
            taskRun,
            execution
        );

        return subGraph;
    }

    @Override
    public List<Task> allChildTasks() {
        return Stream
            .concat(
                this.getTasks() != null ? this.getTasks().stream() : Stream.empty(),
                this.errors != null ? this.errors.stream() : Stream.empty()
            )
            .collect(Collectors.toList());
    }

    @Override
    public List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return FlowableUtils.resolveTasks(this.getTasks(), parentTaskRun);
    }

    @Override
    public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return FlowableUtils.resolveSequentialNexts(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.errors, parentTaskRun),
            parentTaskRun
        );
    }

    public List<Task> getTasks() {
        return List.of(
            new ForEachItemSplit(this.getId(), this.items, this.batch),
            new ForEachItemExecutable(this.getId(), this.inputs, this.inheritLabels, this.labels, this.wait, this.transmitFailed,
                new ExecutableTask.SubflowId(this.namespace, this.flowId, Optional.ofNullable(this.revision))
            ),
            new ForEachItemMergeOutputs(this.getId())
        );
    }

    @SuppressWarnings("unused")
    public void setTasks(List<Task> tasks) {
        // This setter is needed for the serialization framework, but the list is hardcoded in the getter anyway.
    }

    @Hidden
    @Getter
    @NoArgsConstructor
    public static class ForEachItemSplit extends Task implements RunnableTask<ForEachItemSplit.Output> {
        private String items;
        private Batch batch;

        private ForEachItemSplit(String parentId, String items, Batch batch) {
            this.items = items;
            this.batch = batch;

            this.id = parentId + "_split";
            this.type = ForEachItemSplit.class.getName();
        }

        @Override
        public ForEachItemSplit.Output run(RunContext runContext) throws Exception {
            var renderedUri = runContext.render(this.items);
            if (!renderedUri.startsWith("kestra://")) {
                var errorMessage = "Unable to split the items from " + renderedUri + ", this is not an internal storage URI!";
                runContext.logger().error(errorMessage);
                throw new IllegalArgumentException(errorMessage);
            }

            List<URI> splits = StorageService.split(runContext, this.batch, URI.create(renderedUri));
            String fileContent = splits.stream().map(uri -> uri.toString()).collect(Collectors.joining(System.lineSeparator()));
            try (ByteArrayInputStream bis = new ByteArrayInputStream(fileContent.getBytes())){
                URI splitsFile = runContext.storage().putFile(bis, "splits.txt");
                return Output.builder().splits(splitsFile).build();
            }
        }

        @Builder
        @Getter
        public static class Output implements io.kestra.core.models.tasks.Output {
            private URI splits;
        }
    }

    @Hidden
    @Getter
    @NoArgsConstructor
    public static class ForEachItemExecutable extends Task implements ExecutableTask<Output> {
        private Map<String, Object> inputs;
        private Boolean inheritLabels;
        private Map<String, String> labels;
        private Boolean wait;
        private Boolean transmitFailed;
        private SubflowId subflowId;

        private ForEachItemExecutable(String parentId, Map<String, Object> inputs, Boolean inheritLabels, Map<String, String> labels, Boolean wait, Boolean transmitFailed, SubflowId subflowId) {
            this.inputs = inputs;
            this.inheritLabels = inheritLabels;
            this.labels = labels;
            this.wait = wait;
            this.transmitFailed = transmitFailed;
            this.subflowId = subflowId;

            this.id = parentId + "_executable";
            this.type = ForEachItemExecutable.class.getName();
        }

        @Override
        public List<SubflowExecution<?>> createSubflowExecutions(
            RunContext runContext,
            FlowExecutorInterface flowExecutorInterface,
            Flow currentFlow,
            Execution currentExecution,
            TaskRun currentTaskRun
        ) throws InternalException {
            // get the list of splits from the outputs of the split task
            String taskId = this.id.replace("_executable", "_split");
            var taskOutput = extractOutput(runContext, taskId);
            URI splitsURI = URI.create((String) taskOutput.get("splits"));

            try (InputStream is = runContext.storage().getFile(splitsURI)){
                String fileContent = new String(is.readAllBytes());
                List<URI> splits = fileContent.lines().map(line -> URI.create(line)).toList();
                AtomicInteger currentIteration = new AtomicInteger(1);

                return splits
                    .stream()
                    .<SubflowExecution<?>>map(throwFunction(
                        split -> {
                            int iteration = currentIteration.getAndIncrement();
                            // these are special variable that can be passed to the subflow
                            Map<String, Object> itemsVariable = Map.of("taskrun",
                                Map.of("items", split, "iteration", iteration));
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

                            // these are special outputs to be able to compute the iteration map of the parent taskrun
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
                    .iterations((Map<State.Type, Integer>) taskRun.getOutputs().get(ExecutableUtils.TASK_VARIABLE_ITERATIONS))
                    .numberOfBatches((Integer) taskRun.getOutputs().get(ExecutableUtils.TASK_VARIABLE_NUMBER_OF_BATCHES));

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
            return this.subflowId;
        }
    }

    @Hidden
    @Getter
    @NoArgsConstructor
    public static class ForEachItemMergeOutputs extends Task implements RunnableTask<ForEachItemMergeOutputs.Output> {

        private ForEachItemMergeOutputs(String parentId) {
            this.id = parentId + "_merge";
            this.type = ForEachItemMergeOutputs.class.getName();
        }

        @Override
        public ForEachItemMergeOutputs.Output run(RunContext runContext) throws Exception {
            // get the list of splits from the outputs of the split task
            String taskId = this.id.replace("_merge", "_executable");
            var taskOutput = extractOutput(runContext, taskId);
            Integer iterations = (Integer) taskOutput.get(ExecutableUtils.TASK_VARIABLE_NUMBER_OF_BATCHES);
            String subflowOutputsBaseUri = (String) taskOutput.get(ExecutableUtils.TASK_VARIABLE_SUBFLOW_OUTPUTS_BASE_URI);

            List<URI> outputsURIs = IntStream.rangeClosed(1, iterations)
                .mapToObj(it -> "kestra://" + subflowOutputsBaseUri + "/" + it + "/outputs.ion")
                .map(throwFunction(URI::create))
                .filter(runContext.storage()::isFileExist)
                .toList();

            if (!outputsURIs.isEmpty()) {
                // Merge outputs from each sub-flow into a single stored in the internal storage.
                List<InputStream> streams = outputsURIs.stream()
                    .map(throwFunction(runContext.storage()::getFile))
                    .toList();
                try (InputStream is = new SequenceInputStream(Collections.enumeration(streams))) {
                    URI uri = runContext.storage().putFile(is, "outputs.ion");
                    return ForEachItemMergeOutputs.Output.builder().subflowOutputs(uri).build();
                }
            }

            return null;
        }

        @Builder
        @Getter
        public static class Output implements io.kestra.core.models.tasks.Output {
            private URI subflowOutputs;
        }
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractOutput(RunContext runContext, String taskId) {
        var outputVariables = (Map<String, Map<String, Object>>) runContext.getVariables().get("outputs");
        var splitTaskOutput = outputVariables.get(taskId);
        if (runContext.getVariables().containsKey("parent")) {
            // get the parent taskrun value as the value is in the ForEachItem not in one of its subtasks
            var parent = (Map<String, Map<String, Object>>) runContext.getVariables().get("parent");
            String value = (String) parent.get("taskrun").get("value");
            splitTaskOutput = (Map<String, Object>) splitTaskOutput.get(value);
        }
        return splitTaskOutput;
    }
}
