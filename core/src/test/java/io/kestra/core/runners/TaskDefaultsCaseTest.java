package io.kestra.core.runners;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.models.hierarchies.RelationType;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.services.GraphService;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@Singleton
public class TaskDefaultsCaseTest {
    @Inject
    private RunnerUtils runnerUtils;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logQueue;

    public void taskDefaults() throws TimeoutException {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "task-defaults", Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(8));

        assertThat(execution.getTaskRunList().get(0).getTaskId(), is("first"));
        assertThat(execution.getTaskRunList().get(0).getOutputs().get("def"), is("1"));
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

    public void invalidTaskDefaults() throws TimeoutException {
        List<LogEntry> logs = new ArrayList<>();
        logQueue.receive(logs::add);

        Execution execution = runnerUtils.runOne("io.kestra.tests", "invalid-task-defaults", Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(logs.stream().filter(logEntry -> logEntry.getMessage().contains("Unrecognized field \"invalid\"")).count(), greaterThan(0L));
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
        @NotEmpty
        private List<Task> tasks;

        private String def;

        @Override
        public GraphCluster tasksTree(Execution execution, TaskRun taskRun, List<String> parentValues) throws IllegalVariableEvaluationException {
            GraphCluster subGraph = new GraphCluster(this, taskRun, parentValues, RelationType.SEQUENTIAL);

            GraphService.sequential(
                subGraph,
                this.tasks,
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
                    this.tasks != null ? this.tasks.stream() : Stream.empty(),
                    this.errors != null ? this.errors.stream() : Stream.empty()
                )
                .collect(Collectors.toList());
        }

        @Override
        public List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
            return FlowableUtils.resolveTasks(this.tasks, parentTaskRun);
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

        @Override
        public Output outputs(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
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
