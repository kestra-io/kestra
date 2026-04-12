package io.kestra.plugin.core.flow;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.models.hierarchies.RelationType;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.FlowableUtils;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.Either;
import io.kestra.core.utils.GraphUtils;
import io.kestra.core.utils.MapUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute child tasks for each value in a list.",
    description = """
        Renders `values` (JSON array, YAML list, or expression) and runs the child task group once per item. The current item is available as `item.value`; `item.index` exposes the index.

        Control parallelism with `concurrencyLimit` (0 = unlimited, 1 = fully serialized, N = up to N concurrent task groups). To run tasks inside each group in parallel, wrap them in a `Parallel` task."""
)
@Plugin(
    examples = {
        @Example(
            full = true,
            title = """
                The `{{ item.value }}` from the `loop` task is available only to direct child tasks \
                such as the `before_if` and the `if` tasks.""",
            code = """
                id: for_loop_example
                namespace: company.team

                tasks:
                  - id: loop
                    type: io.kestra.plugin.core.flow.Loop
                    values: ["value 1", "value 2", "value 3"]
                    tasks:
                      - id: before_if
                        type: io.kestra.plugin.core.debug.Return
                        format: "Before if {{ item.value }}"
                      - id: if
                        type: io.kestra.plugin.core.flow.If
                        condition: '{{ item.value == "value 2" }}'
                        then:
                          - id: after_if
                            type: io.kestra.plugin.core.debug.Return
                            format: "After if {{ item.value }}"
                """
        ),
        @Example(
            full = true,
            title = """
                This flow uses YAML-style array for `values`. The task `loop` iterates over a list of values \
                and executes the `return` child task for each value. The `concurrencyLimit` property is set to 2, \
                so the `return` task will run concurrently for the first two values in the list at first. \
                The `return` task will run for the next two values only after the task runs for the first two values \
                have completed.""",
            code = """
                id: for_each_value
                namespace: company.team

                tasks:
                  - id: for_each
                    type: io.kestra.plugin.core.flow.Loop
                    values:
                      - value 1
                      - value 2
                      - value 3
                      - value 4
                    concurrencyLimit: 2
                    tasks:
                      - id: return
                        type: io.kestra.plugin.core.debug.Return
                        format: "{{ task.id }} with value {{ item.value }}"
                """
        ),
        @Example(
            full = true,
            title = """
                This example shows how to run tasks in parallel for each value in the list. \
                All child tasks of the `parallel` task will run in parallel. \
                However, due to the `concurrencyLimit` property set to 2, \
                only two `parallel` task groups will run at any given time.""",
            code = """
                id: parallel_tasks_example
                namespace: company.team

                tasks:
                  - id: for_each
                    type: io.kestra.plugin.core.flow.Loop
                    values: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
                    concurrencyLimit: 2
                    tasks:
                      - id: parallel
                        type: io.kestra.plugin.core.flow.Parallel
                        tasks:
                        - id: log
                          type: io.kestra.plugin.core.log.Log
                          message: Processing {{ item.value }}
                        - id: shell
                          type: io.kestra.plugin.scripts.shell.Commands
                          commands:
                            - sleep {{ item.value }}
                """
        ),
        @Example(
            full = true,
            title = """
                This example demonstrates processing data across nested loops of S3 buckets, years, and months. \
                It generates structured identifiers (e.g., `bucket1_2025_March`) by combining values from each loop level, \
                while accessing parent loop values like years and buckets, which can be useful for partitioned \
                storage paths or time-based datasets. The flow uses dynamic expressions referencing parent context.""",
            code = """
                id: loop_multiple_times
                namespace: company.team

                inputs:
                  - id: s3_buckets
                    type: ARRAY
                    itemType: STRING
                    defaults:
                      - bucket1
                      - bucket2

                  - id: years
                    type: ARRAY
                    itemType: INT
                    defaults:
                      - 2025
                      - 2026

                  - id: months
                    type: ARRAY
                    itemType: STRING
                    defaults:
                      - March
                      - April

                tasks:
                  - id: buckets
                    type: io.kestra.plugin.core.flow.Loop
                    values: "{{inputs.s3_buckets}}"
                    tasks:
                      - id: year
                        type: io.kestra.plugin.core.flow.Loop
                        values: "{{inputs.years}}"
                        tasks:
                          - id: month
                            type: io.kestra.plugin.core.flow.Loop
                            values: "{{inputs.months}}"
                            tasks:
                              - id: full_table_name
                                type: io.kestra.plugin.core.log.Log
                                message: |
                                  Full table name: {{item.parents[1].item.value }}_{{item.parent.value}}_{{item.value}}
                                  Direct/current loop (months): {{item.value}}
                                  Value of loop one higher up (years): {{item.parents[0].value}}
                                  Further up (table types): {{item.parents[1].value}}
                """
        ),
    }
)
public class Loop extends Task implements FlowableTask<Loop.Output> {
    public static final String ITERATION_COUNT_OUTPUT = "iterationCount";
    public static final String RUNNING_ITERATIONS_OUTPUT = "runningIterations";
    public static final String TERMINATED_ITERATIONS_OUTPUT = "terminatedIterations";
    public static final String NEXT_OFFSET_OUTPUT = "nextOffset";

    @Valid
    protected List<Task> errors;

    @Valid
    @JsonProperty("finally")
    @Getter(AccessLevel.NONE)
    protected List<Task> _finally;

    public List<Task> getFinally() {
        return this._finally;
    }

    @Valid
    @PluginProperty
    @NotEmpty(message = "The 'tasks' property cannot be empty")
    private List<Task> tasks;

    @NotNull
    @PluginProperty(dynamic = true)
    @Schema(
        title = "The list of values for which Kestra will execute a group of tasks",
        description = """
            Values can be defined as:
            - A list of objects, individual objects will be coalesced to strings
            - A string which will be deserialized as a JSON array
            - An ION file URI, each line will be deserialized as an ION object then coalesced to a string""",
        oneOf = { String.class, Object[].class }
    )
    private Object values;

    @PositiveOrZero
    @NotNull
    @Builder.Default
    @Schema(
        title = "The number of concurrent task groups for each value in the `values` array",
        description = """
            A `concurrencyLimit` of 0 means no limit — all task groups run in parallel.

            A `concurrencyLimit` of 1 means full serialization — only one task group runs at a time, in order.

            A `concurrencyLimit` greater than 1 allows up to the specified number of task groups to run in parallel.
            """
    )
    @PluginProperty
    private final Integer concurrencyLimit = 1;

    @Builder.Default
    @Schema(
        title = "Flag specifying whether to fail the current task if any loop iteration fails or is killed."
    )
    @PluginProperty
    private final Boolean transmitFailed = true;

    // FIXME there are a lot of duplication with Sequential but as it needs to return a different output it cannot extend it

    @Override
    public GraphCluster tasksTree(Execution execution, TaskRun taskRun, List<String> parentValues) throws IllegalVariableEvaluationException {
        GraphCluster subGraph = new GraphCluster(this, taskRun, parentValues, RelationType.DYNAMIC);

        // Loop executes task groups concurrently, not the task inside the group concurrently,
        // so the topology should display it as a sequential.
        GraphUtils.sequential(
            subGraph,
            this.getTasks(),
            this.getErrors(),
            this.getFinally(),
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
                Stream.concat(
                    this.getErrors() != null ? this.getErrors().stream() : Stream.empty(),
                    this.getFinally() != null ? this.getFinally().stream() : Stream.empty()
                )
            )
            .toList();
    }

    @Override
    public List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return FlowableUtils.resolveTasks(this.getTasks(), parentTaskRun);
    }

    @Override
    public Optional<State.Type> resolveState(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        if (!isMySubExecution(execution, parentTaskRun)) {
            // Not in this loop's own sub-execution — state is managed by the TerminatedLoopExecutionMessageHandler.
            return Optional.empty();
        }

        List<ResolvedTask> childTasks = this.childTasks(runContext, parentTaskRun);

        return FlowableUtils.resolveSequentialState(
            execution,
            childTasks,
            FlowableUtils.resolveTasks(this.getErrors(), parentTaskRun),
            FlowableUtils.resolveTasks(this.getFinally(), parentTaskRun),
            parentTaskRun,
            runContext,
            this.isAllowFailure(),
            this.isAllowWarning()
        );
    }

    @Override
    public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        if (!isMySubExecution(execution, parentTaskRun)) {
            // We are not in this loop's own sub-execution (either we're in the main execution,
            // or in a parent loop's sub-execution), so we don't resolve any next tasks to
            // avoid executing subtasks outside of the dedicated loop iteration execution.
            return Collections.emptyList();
        }

        return FlowableUtils.resolveSequentialNexts(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.errors, parentTaskRun),
            FlowableUtils.resolveTasks(this._finally, parentTaskRun),
            parentTaskRun
        );
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The counter of iterations for each loop branch execution"
        )
        private Integer iterations;
    }

    @Override
    public Output outputs(RunContext runContext) throws Exception {
        var currentOutputs = runContext.currentOutput();
        if (!MapUtils.isEmpty(currentOutputs) && currentOutputs.containsKey("iterations")) {
            Integer iterations = (Integer) currentOutputs.get("iterations");
            return Output.builder().iterations(iterations).build();
        } else {
            return Output.builder().iterations(0).build();
        }
    }

    public boolean isMySubExecution(Execution execution, TaskRun parentTaskRun) {
        return execution.getKind() == ExecutionKind.LOOP &&
            execution.getLoopRun() != null && execution.getLoopRun().taskRunId().equals(parentTaskRun.getId());
    }

    /**
     * Computes initialization data for URI-backed Loop values (ION file mode).
     * Reads the first batch of values and counts the total in a single file pass.
     *
     * @param runContext the run context
     * @param valuesUri  the rendered URI pointing to the ION file
     * @return a {@link UriInit} holding totalCount, active limit, first batch of values, and next byte offset
     */
    @VisibleForTesting
    public UriInit initFromUri(RunContext runContext, String valuesUri) throws IOException, IllegalVariableEvaluationException {
        int rawLimit = this.concurrencyLimit == 0 ? Integer.MAX_VALUE : this.concurrencyLimit;
        var init = FlowableUtils.readAndCountLoopValuesFromUri(runContext, valuesUri, rawLimit);
        int size = init.totalCount();
        int limit = Math.min(this.concurrencyLimit == 0 ? size : this.concurrencyLimit, size);
        return new UriInit(size, limit, init.values(), init.nextOffset());
    }

    /**
     * Computes initialization data for in-memory Loop values (list or map mode).
     * Resolves all values eagerly and applies the concurrency limit.
     *
     * @param runContext the run context
     * @return a {@link ValuesInit} holding totalCount, active limit, and resolved values
     */
    @VisibleForTesting
    public ValuesInit initFromValues(RunContext runContext) throws IllegalVariableEvaluationException {
        var either = FlowableUtils.resolveValues(runContext, this.values);
        int size = either.isLeft() ? either.getLeft().size() : either.getRight().size();
        int limit = this.concurrencyLimit == 0 ? size : Math.min(this.concurrencyLimit, size);
        return new ValuesInit(size, limit, either);
    }

    /** Holds initialization data computed from a URI-backed ION file. */
    public record UriInit(int totalCount, int limit, List<String> values, long nextOffset) {}

    /** Holds initialization data computed from in-memory (list or map) values. */
    public record ValuesInit(int totalCount, int limit, Either<List<String>, List<Pair<String, String>>> values) {}
}
