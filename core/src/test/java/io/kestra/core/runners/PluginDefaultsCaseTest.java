package io.kestra.core.runners;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.models.hierarchies.RelationType;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.utils.GraphUtils;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import lombok.experimental.SuperBuilder;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@Singleton
public class PluginDefaultsCaseTest {
    @Inject
    private TestRunnerUtils runnerUtils;

    @Inject
    private FlowRepositoryInterface flowRepository;

    public void pluginDefaultsRefNotFound() throws TimeoutException, QueueException, io.kestra.core.exceptions.InternalException {
        // FlowService.create/update reject unresolved refs, so the only way an unresolved ref reaches runtime is a
        // flow that became invalid after creation (e.g. its namespace plugin-defaults was removed). We reproduce
        // that by storing the flow directly through the repository, which bypasses the ref validation.
        String source = """
            id: plugin-defaults-ref-not-found
            namespace: io.kestra.tests
            tasks:
              - id: broken
                type: io.kestra.plugin.core.debug.Return
                format: "should never run"
                pluginDefaultsRef: does-not-exist
            """;
        FlowWithSource created = flowRepository.create(GenericFlow.fromYaml(MAIN_TENANT, source));

        try {
            Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "plugin-defaults-ref-not-found", Duration.ofSeconds(60));

            // the task references a pluginDefaultsRef that does not resolve: it must fail at runtime
            assertThat(execution.getState().getCurrent()).isEqualTo(io.kestra.core.models.flows.State.Type.FAILED);
            assertThat(execution.getTaskRunList()).hasSize(1);
            assertThat(execution.getTaskRunList().getFirst().getTaskId()).isEqualTo("broken");
            assertThat(execution.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(io.kestra.core.models.flows.State.Type.FAILED);
        } finally {
            flowRepository.delete(created);
        }
    }

    public void taskDefaults() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "plugin-defaults", Duration.ofSeconds(60));

        assertThat(execution.getTaskRunList()).hasSize(8);

        assertThat(execution.getTaskRunList().getFirst().getTaskId()).isEqualTo("first");
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("def")).isEqualTo("1");
        assertThat(execution.getTaskRunList().get(1).getTaskId()).isEqualTo("second");
        assertThat(execution.getTaskRunList().get(1).getOutputs().get("def")).isEqualTo("2");
        assertThat(execution.getTaskRunList().get(2).getTaskId()).isEqualTo("third");
        assertThat(execution.getTaskRunList().get(2).getOutputs().get("def")).isEqualTo("3");

        assertThat(execution.getTaskRunList().get(4).getTaskId()).isEqualTo("err-first");
        assertThat(execution.getTaskRunList().get(4).getOutputs().get("def")).isEqualTo("1");
        assertThat(execution.getTaskRunList().get(5).getTaskId()).isEqualTo("err-second");
        assertThat(execution.getTaskRunList().get(5).getOutputs().get("def")).isEqualTo("2");
        assertThat(execution.getTaskRunList().get(6).getTaskId()).isEqualTo("err-third");
        assertThat(execution.getTaskRunList().get(6).getOutputs().get("def")).isEqualTo("3");
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
