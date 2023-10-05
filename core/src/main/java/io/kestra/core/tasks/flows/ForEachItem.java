package io.kestra.core.tasks.flows;

import com.fasterxml.jackson.core.JsonProcessingException;
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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private static final int BUFFER_SIZE = 8 * 1024; // 8KB
    private static final String STATE_STATE = "for-each-item-state";
    private static final String STATE_NAME = "offsets";

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

    @NotNull
    @PluginProperty
    private SubFlow subFlow;

    @Override
    @Hidden
    public List<Task> getErrors() {
        return Collections.emptyList();
    }

    @Schema(hidden = true)
    private List<BatchOffsets> offsets;

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

        return FlowableUtils.resolveState(
            execution,
            childTasks,
            FlowableUtils.resolveTasks(this.getErrors(), parentTaskRun),
            parentTaskRun,
            runContext
        );
    }

    @Override
    public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        List<NextTaskRun> next;
        if (maxConcurrency == 1) {
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
        try (var bis = new BufferedInputStream(runContext.uriToInputStream(data))) {
            BatchOffsets batchOffsets = JacksonMapper.ofJson().readValue(value, BatchOffsets.class);
            int nbRead = batchOffsets.endOffset - batchOffsets.startOffset;
            byte[] bytes = new byte[nbRead];
            bis.skip(batchOffsets.startOffset);
            bis.read(bytes);
            String lines = new String(bytes);
            return lines.lines().toList();
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

    private List<String> getValues(RunContext runContext) {
        try {
            return getOffsets(runContext).stream().map(throwFunction(b -> JacksonMapper.ofJson().writeValueAsString(b))).toList();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private List<BatchOffsets> getOffsets(RunContext runContext) {
        if (this.offsets == null) {
            // must be inside the state as we create it when initializing the state
            try (InputStream is = runContext.getTaskStateFile(STATE_STATE, STATE_NAME)) {
                this.offsets = JacksonMapper.ofJson().readValue(is, new TypeReference<>() {});
            }
            catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
        return this.offsets;
    }

    @Override
    public void init(RunContext runContext) throws IllegalVariableEvaluationException {
        this.offsets = readOffsets(runContext);
        // TODO if the UI is not updated, add a check to limit the number of batch to avoid having a non-responsive UI
        try {
            byte[] content = JacksonMapper.ofJson().writeValueAsBytes(this.offsets);
            runContext.putTaskStateFile(content, STATE_STATE, STATE_NAME);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<BatchOffsets> readOffsets(RunContext runContext) throws IllegalVariableEvaluationException {
        List<BatchOffsets> batchOffsets = new ArrayList<>();

        URI data = URI.create(runContext.render(this.items));
        try (var bis = new BufferedInputStream(runContext.uriToInputStream(data))) {
            int batch = 1; // file current line offset
            int lineNb = 0;
            int batchStartOffset = 0;
            int batchEndOffset = 0;
            int c;
            while ((c = bis.read()) != -1) {
                if (c == '\n' || c == '\r') {
                    lineNb++;
                }
                if (lineNb == maxItemsPerBatch) {
                    batchOffsets.add(new BatchOffsets(String.valueOf(batch), batchStartOffset, batchEndOffset));
                    batch++;
                    batchStartOffset = batchEndOffset + 1;
                    lineNb = 0;
                }

                batchEndOffset++;
            }

            if (batchStartOffset != batchEndOffset) {
                // there is one additional partial batch
                batchOffsets.add(new BatchOffsets(String.valueOf(batch), batchStartOffset, batchEndOffset));
            }

            return batchOffsets;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private record BatchOffsets(String value, int startOffset, int endOffset) {}

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
