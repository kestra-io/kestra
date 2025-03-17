package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.models.hierarchies.RelationType;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.queues.QueueException;
import io.kestra.core.utils.GraphUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@Singleton
public class PluginDefaultsCaseTest {
    @Inject
    private RunnerUtils runnerUtils;

    public void taskDefaults() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "plugin-defaults", Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(8));

        assertThat(execution.getTaskRunList().getFirst().getTaskId(), is("first"));
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("def"), is("1"));
        assertThat(execution.getTaskRunList().get(1).getTaskId(), is("second"));
        assertThat(execution.getTaskRunList().get(1).getOutputs().get("def"), is("2"));
        assertThat(execution.getTaskRunList().get(2).getTaskId(), is("third"));
        assertThat(execution.getTaskRunList().get(2).getOutputs().get("def"), is("3"));

        assertThat(execution.getTaskRunList().get(4).getTaskId(), is("err-first"));
        assertThat(execution.getTaskRunList().get(4).getOutputs().get("def"), is("1"));
        assertThat(execution.getTaskRunList().get(5).getTaskId(), is("err-second"));
        assertThat(execution.getTaskRunList().get(5).getOutputs().get("def"), is("2"));
        assertThat(execution.getTaskRunList().get(6).getTaskId(), is("err-third"));
        assertThat(execution.getTaskRunList().get(6).getOutputs().get("def"), is("3"));
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class DefaultSequential1 extends Task implements FlowableTask<DefaultSequential1.Output> {
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
        @NotEmpty
        private List<Task> tasks;

        private String def;

        @Override
        public GraphCluster tasksTree(Execution execution, TaskRun taskRun, List<String> parentValues) throws IllegalVariableEvaluationException {
            GraphCluster subGraph = new GraphCluster(this, taskRun, parentValues, RelationType.SEQUENTIAL);

            GraphUtils.sequential(
                subGraph,
                this.tasks,
                this.errors,
                this._finally,
                taskRun,
                execution
            );

            return subGraph;
        }

        @Override
        public List<Task> allChildTasks() {
            return Stream
                .concat(
                    this.tasks != null ? this.tasks.stream() : Stream.empty(),
                    Stream.concat(
                        this.errors != null ? this.errors.stream() : Stream.empty(),
                        this._finally != null ? this._finally.stream() : Stream.empty()
                    )
                )
                .toList();
        }

        @Override
        public List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) {
            return FlowableUtils.resolveTasks(this.tasks, parentTaskRun);
        }

        @Override
        public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) {
            return FlowableUtils.resolveSequentialNexts(
                execution,
                this.childTasks(runContext, parentTaskRun),
                FlowableUtils.resolveTasks(this.errors, parentTaskRun),
                FlowableUtils.resolveTasks(this._finally, parentTaskRun),
                parentTaskRun
            );
        }

        @Override
        public Output outputs(RunContext runContext) throws IllegalVariableEvaluationException {
            return Output.builder()
                .def(this.def)
                .build();
        }

        @SuperBuilder
        @Getter
        public static class Output implements io.kestra.core.models.tasks.Output {
            private final String def;
        }
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class DefaultSequential2 extends DefaultSequential1 {
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    public static class DefaultSequential3 extends DefaultSequential1 {
    }
}
