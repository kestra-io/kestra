package io.kestra.core.tasks.flows;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.models.hierarchies.RelationType;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.FlowableUtils;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.GraphUtils;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
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
    title = "TODO",
    description = "TODO"
)
@Plugin(
    examples = {
        @Example(
            title = "TODO",
            code = {
                """
                    id: each
                    type: io.kestra.core.tasks.flows.ForEachItem
                    values: "{{ outputs.extract.uri }}" # works with API payloads too. Kestra can detect if this output is not a file,\s
                    # and will make it to a file, split into (batches of) items
                    maxItemsPerBatch: 10
                    maxConcurrency: 5 # max 5 concurrent executions, each processing 10 items
                    allowedFailureThreshold: # optional argument allowing to specify how many executions are allowed to fail e.g. 20% of executions are allowed,\s
                        # or 10 Executions can fail because we know those are known outliers. can be expressed either through the percentage or number of items
                       percent: 20 # integer value between 1 and 100
                       items: 10 # arbitrary INTEGER value
                    subflow: # optional
                      flowId: file
                      namespace: dev
                      inputs:
                        file: "{{ taskrun.value }}"
                      wait: true # wait by default
                      transmitFailed: true # true by default"""
            }
        )
    }
)
public class ForEachItem extends Task implements FlowableTask<VoidOutput> {

    @NotEmpty
    @PluginProperty(dynamic = true)
    private String items;

    @Positive
    @NotNull
    @PluginProperty
    @Builder.Default
    private Integer maxItemsPerBatch = 10;

    @Min(0)
    @NotNull
    @PluginProperty
    @Builder.Default
    private Integer maxConcurrency = 1;

    @PluginProperty
    private AllowedFailureThreshold allowedFailureThreshold;

    @NotNull
    @PluginProperty
    private SubFlow subFlow;

    @Override
    @Hidden
    public List<Task> getErrors() {
        return Collections.emptyList();
    }

    @Schema(hidden = true)
    private List<String> values;

    @Override
    public GraphCluster tasksTree(Execution execution, TaskRun taskRun, List<String> parentValues) throws IllegalVariableEvaluationException {
        GraphCluster subGraph = new GraphCluster(this, taskRun, parentValues, RelationType.SEQUENTIAL);

        GraphUtils.sequential(
            subGraph,
            getTasks(),
            getErrors(),
            taskRun,
            execution
        );

        return subGraph;
    }

    @Override
    public List<Task> allChildTasks() {
        return this.getTasks();
    }

    @Override
    public List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return FlowableUtils.resolveEachTasks(runContext, parentTaskRun, this.getTasks(), getValues(runContext));
    }

    @Override
    public Optional<State.Type> resolveState(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        List<ResolvedTask> childTasks = this.childTasks(runContext, parentTaskRun);

        if (childTasks.isEmpty()) {
            return Optional.of(State.Type.SUCCESS);
        }

        // standard resolveState that is used on other flowable, we use it if there is no allowedFailureThreshold configured
        if (allowedFailureThreshold == null) {
            return FlowableUtils.resolveState(
                execution,
                childTasks,
                FlowableUtils.resolveTasks(this.getErrors(), parentTaskRun),
                parentTaskRun,
                runContext
            );
        }

        // TODO: add validation to avoid as much as possible this exception
        if (allowedFailureThreshold.percent == null && allowedFailureThreshold.items == null) {
            throw new IllegalArgumentException("When setting 'allowedFailureThreshold', one of 'percent' or 'items' mut be set");
        }

        // ---
        // Starting here, the code is coming from FlowableUtils.resolveState but adapted to handle partial failure
        // ---

        List<ResolvedTask> currentTasks = execution.findTaskDependingFlowState(childTasks, Collections.emptyList(), parentTaskRun);

        if (currentTasks == null) {
            runContext.logger().warn(
                "No task found on flow '{}', task '{}', execution '{}'",
                execution.getNamespace() + "." + execution.getFlowId(),
                parentTaskRun.getTaskId(),
                execution.getId()
            );

            return Optional.of(State.Type.FAILED);
        } else if (!currentTasks.isEmpty()) {
            // handle nominal case, tasks or errors flow are ready to be analysed
            if (execution.isTerminated(currentTasks, parentTaskRun)) {
                return guessFinalState(execution, currentTasks, parentTaskRun, allowedFailureThreshold);
            }
        } else {
            // first call, the error flow is not ready, we need to notify the parent task that can be failed to init error flows
            if (execution.hasFailed(childTasks, parentTaskRun)) {
                // value is the batch number, we fetch the last value to know the approximative size of the file
                Integer maxValue = execution.findTaskRunByTasks(childTasks, parentTaskRun).stream()
                    .map(t -> Integer.parseInt(t.getValue()))
                    .max(Integer::compareTo)
                    .get();

                // FIXME it still stops at the first batch of failed tasks :(
                // we first need to check if more tasks must be executed before being able to guess the final state
                if (allowedFailureThreshold.items != null && ((long) maxValue * maxItemsPerBatch) < allowedFailureThreshold.items) {
                    return Optional.empty();
                }
                // FIXME we should not read the values so many times :(
                else if (allowedFailureThreshold.percent != null && maxValue < ((long) allowedFailureThreshold.percent * maxValue * maxItemsPerBatch / 100L)) {
                    return Optional.empty();
                }

                // if not, we can try to guess the final state
                return guessFinalState(execution, childTasks, parentTaskRun, allowedFailureThreshold);
            }
        }

        return Optional.empty();
    }

    private Optional<State.Type> guessFinalState(Execution execution, List<ResolvedTask> tasks, TaskRun parentTaskRun, @NotNull AllowedFailureThreshold allowedFailureThreshold) {
        var state = execution.guessFinalState(tasks, parentTaskRun);
        if (state == State.Type.FAILED) {
            // We will count the number of failed task to define if the flow should end in FAILED or WARNING depending on the allowedFailureThreshold.
            // Note that currently it approximates the number of failed items to maxItemsPerBatch * nbTasks which is wrong as if the number of items
            // is not a multiple of the number of task the last task will have less than maxItemsPerBatch items.
            long failed = execution.findTaskRunByTasks(tasks, parentTaskRun).stream().filter(t -> t.getState().getCurrent() == State.Type.FAILED).count() *  maxItemsPerBatch;
            if (allowedFailureThreshold.items != null && failed > allowedFailureThreshold.items) {
                return Optional.of(State.Type.FAILED);
            }
            else if (allowedFailureThreshold.percent != null && (tasks.size() * 100L / failed) > allowedFailureThreshold.percent) {
                return Optional.of(State.Type.FAILED);
            }

            return Optional.of(State.Type.WARNING);
        }
        return Optional.of(state);
    }

    @Override
    public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        // FIXME when there is a FAILED task, resolveNexts returns an empty list so allowedFailureThreshold didn't work
        List<NextTaskRun> next;
        if (maxConcurrency == 0) {
             next = FlowableUtils.resolveSequentialNexts(
                execution,
                FlowableUtils.resolveEachTasks(runContext, parentTaskRun, this.getTasks(), getValues(runContext)),
                FlowableUtils.resolveTasks(this.getErrors(), parentTaskRun),
                parentTaskRun
            );
        }
        else {
            next = FlowableUtils.resolveParallelNexts(
                execution,
                FlowableUtils.resolveEachTasks(runContext, parentTaskRun, this.getTasks(), getValues(runContext)),
                FlowableUtils.resolveTasks(this.getErrors(), parentTaskRun),
                parentTaskRun,
                maxConcurrency
            );
        }

        // add an input for the items
        return next.stream()
            .map(throwFunction(n -> n.withTaskRun(n.getTaskRun().withItems(readItems(runContext, n.getTaskRun().getValue())))))
            .toList();
    }

    private List<String> readItems(RunContext runContext, String value) throws IllegalVariableEvaluationException {
        URI data = URI.create(runContext.render(this.items));
        try (var reader = new BufferedReader(new InputStreamReader(runContext.uriToInputStream(data)));
            var lines = reader.lines()) {
            // TODO we need to read the file once and record somewhere the start and end offsets of each batch
            // Another idea would be to build an URI with start and end offset and enhance the internal storage to be able to read part of a file
            int batchNumber = Integer.parseInt(value);
            int startLine = (batchNumber - 1) * maxItemsPerBatch;
            return lines.skip(startLine).limit(maxItemsPerBatch).toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Task> getTasks() {
        return List.of(createFlowTask());
    }

    private Task createFlowTask() {
        return Flow.builder()
            .id(this.getId() + "-subflow")
            .type(Flow.class.getName())
            .namespace(this.subFlow.namespace)
            .flowId(this.subFlow.flowId)
            .revision(this.subFlow.revision)
            .inputs(this.subFlow.inputs)
            .labels(this.subFlow.labels)
            .wait(this.subFlow.wait)
            .transmitFailed(this.subFlow.transmitFailed)
            .inheritLabels(this.subFlow.inheritLabels)
            .build();
    }

    private List<String> getValues(RunContext runContext) throws IllegalVariableEvaluationException {
        if (this.values == null) {
            // try to get it from the task state
            try (InputStream is = runContext.getTaskStateFile("tasks-states", "for-each-item")) {
                this.values = JacksonMapper.ofJson().readValue(is, new TypeReference<>() {});
            }
            catch (IOException e) {
                // FIXME if missing from the state we load it from the items, then store it in the sate
                this.values = readValues(runContext);
                try {
                    byte[] content = JacksonMapper.ofJson().writeValueAsBytes(this.values);
                    runContext.putTaskStateFile(content, "tasks-states", "for-each-item");
                }
                catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
            }
        }
        return this.values;
    }

    private List<String> readValues(RunContext runContext) throws IllegalVariableEvaluationException {
        URI data = URI.create(runContext.render(this.items));
        try (var reader = new LineNumberReader(new InputStreamReader(runContext.uriToInputStream(data)))) {
            reader.skip(Integer.MAX_VALUE);
            int lineNb = reader.getLineNumber();
            int chunckNb = lineNb / maxItemsPerBatch;
            if (lineNb % maxItemsPerBatch > 0) {
                chunckNb++;
            }
            this.values = IntStream.range(1, chunckNb + 1)
                .mapToObj(nb -> String.valueOf(nb))
                .toList();
            return this.values;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class AllowedFailureThreshold {
        @Min(0) @Max(100)
        @PluginProperty
        private Integer percent;

        @Positive
        @PluginProperty
        private Long items;
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class SubFlow {
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
