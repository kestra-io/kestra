package io.kestra.core.services;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.flow.Subflow;

import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
@Execution(ExecutionMode.SAME_THREAD)
class FlowValidationServiceTest {
    private static final String TEST_NAMESPACE = "io.kestra.unittest";

    @Inject
    private FlowValidationService flowValidationService;
    @Inject
    private FlowRepositoryInterface flowRepository;

    private static FlowWithSource create(String flowId, String taskId, Integer revision) {
        return create(null, TEST_NAMESPACE, flowId, taskId, revision);
    }

    private static FlowWithSource create(String tenantId, String namespace, String flowId, String taskId, Integer revision) {
        FlowWithSource flow = FlowWithSource.builder()
            .id(flowId)
            .namespace(namespace)
            .tenantId(tenantId)
            .revision(revision)
            .tasks(
                Collections.singletonList(
                    Return.builder()
                        .id(taskId)
                        .type(Return.class.getName())
                        .format(Property.ofValue("test"))
                        .build()
                )
            )
            .build();

        return flow.toBuilder().source(flow.sourceOrGenerateIfNull()).build();
    }

    @Test
    void warnings() {
        FlowWithSource flow = create("test", "test", 1).toBuilder()
            .namespace("system")
            .triggers(
                List.of(
                    io.kestra.plugin.core.trigger.Flow.builder()
                        .id("flow-trigger")
                        .type(io.kestra.plugin.core.trigger.Flow.class.getName())
                        .build()
                )
            )
            .build();

        List<String> warnings = flowValidationService.warnings(flow, null);

        assertThat(warnings.size()).isEqualTo(1);
        assertThat(warnings).containsExactlyInAnyOrder(
            "This flow will be triggered for EVERY execution of EVERY flow on your instance. We recommend adding the preconditions property to the Flow trigger 'flow-trigger'."
        );
    }

    @Test
    void aliases() {
        List<FlowValidationService.Relocation> warnings = flowValidationService.relocations("""
            id: hello-alias
            namespace: myteam

            tasks:
              - id: log-alias
                type: io.kestra.core.runners.test.task.Alias
                message: Hello, Alias
              - id: log-task
                type: io.kestra.core.runners.test.TaskWithAlias
                message: Hello, Task
              - id: each
                type: io.kestra.plugin.core.flow.ForEach
                values:\s
                  - 1
                  - 2
                  - 3
                tasks:
                  - id: log-alias-each
                    type: io.kestra.core.runners.test.task.Alias
                    message: Hello, {{taskrun.value}}""");

        assertThat(warnings.size()).isEqualTo(2);
        assertThat(warnings.getFirst().from()).isEqualTo("io.kestra.core.runners.test.task.Alias");
        assertThat(warnings.getFirst().to()).isEqualTo("io.kestra.core.runners.test.TaskWithAlias");
    }

    @Test
    void propertyRenamingDeprecation() {
        FlowWithSource flow = FlowWithSource.builder()
            .id("flowId")
            .namespace(TEST_NAMESPACE)
            .tasks(
                Collections.singletonList(
                    DeprecatedTask.builder()
                        .id("taskId")
                        .type(DeprecatedTask.class.getName())
                        .build()
                )
            )
            .build();

        assertThat(flowValidationService.deprecationPaths(flow)).containsExactlyInAnyOrder("tasks[0]");
    }

    @Test
    void checkSubflowNotFound() {
        FlowWithSource flow = create("mainFlow", "task", 1).toBuilder()
            .tasks(
                List.of(
                    Subflow.builder()
                        .id("subflowTask")
                        .type(Subflow.class.getName())
                        .namespace(TEST_NAMESPACE)
                        .flowId("nonExistentSubflow")
                        .build()
                )
            )
            .build();

        List<String> exceptions = flowValidationService.checkValidSubflows(flow, null);

        assertThat(exceptions.size()).isEqualTo(1);
        assertThat(exceptions.iterator().next()).isEqualTo("The subflow 'nonExistentSubflow' not found in namespace 'io.kestra.unittest'.");
    }

    @Test
    void checkValidSubflow() {
        FlowWithSource subflow = create("existingSubflow", "task", 1);
        flowRepository.create(GenericFlow.of(subflow));

        FlowWithSource flow = create("mainFlow", "task", 1).toBuilder()
            .tasks(
                List.of(
                    Subflow.builder()
                        .id("subflowTask")
                        .type(Subflow.class.getName())
                        .namespace(TEST_NAMESPACE)
                        .flowId("existingSubflow")
                        .build()
                )
            )
            .build();

        List<String> exceptions = flowValidationService.checkValidSubflows(flow, null);

        assertThat(exceptions.size()).isZero();
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    @Deprecated
    public static class DeprecatedTask extends Task implements RunnableTask<VoidOutput> {
        @NotBlank
        @PluginProperty(dynamic = true)
        @Deprecated
        private String additionalProperty;

        @Override
        public VoidOutput run(RunContext runContext) throws Exception {
            return null;
        }
    }
}