package io.kestra.core.services;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.property.Property;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.plugin.core.debug.Echo;
import io.kestra.plugin.core.debug.Return;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest
class FlowServiceTest {
    @Inject
    private FlowService flowService;
    @Inject
    private FlowRepositoryInterface flowRepository;

    private static Flow create(String flowId, String taskId, Integer revision) {
        return create(null, flowId, taskId, revision);
    }

    private static Flow create(String tenantId, String flowId, String taskId, Integer revision) {
        return Flow.builder()
            .id(flowId)
            .namespace("io.kestra.unittest")
            .tenantId(tenantId)
            .revision(revision)
            .tasks(Collections.singletonList(Return.builder()
                .id(taskId)
                .type(Return.class.getName())
                .format(Property.of("test"))
                .build()))
            .build();
    }

    @Test
    void importFlow() {
        String source = """
            id: import
            namespace: some.namespace
            tasks:
            - id: task
              type: io.kestra.plugin.core.log.Log
              message: Hello""";
        Flow importFlow = flowService.importFlow("my-tenant", source);

        assertThat(importFlow.getId(), is("import"));
        assertThat(importFlow.getNamespace(), is("some.namespace"));
        assertThat(importFlow.getRevision(), is(1));
        assertThat(importFlow.getTasks().size(), is(1));
        assertThat(importFlow.getTasks().getFirst().getId(), is("task"));

        Optional<FlowWithSource> fromDb = flowRepository.findByIdWithSource("my-tenant", "some.namespace", "import", Optional.empty());
        assertThat(fromDb.isPresent(), is(true));
        assertThat(fromDb.get().getRevision(), is(1));
        assertThat(fromDb.get().getSource(), is(source));

        source = source.replace("id: task", "id: replaced_task");
        importFlow = flowService.importFlow("my-tenant", source);
        assertThat(importFlow.getRevision(), is(2));
        assertThat(importFlow.getTasks().size(), is(1));
        assertThat(importFlow.getTasks().getFirst().getId(), is("replaced_task"));

        fromDb = flowRepository.findByIdWithSource("my-tenant", "some.namespace", "import", Optional.empty());
        assertThat(fromDb.isPresent(), is(true));
        assertThat(fromDb.get().getRevision(), is(2));
        assertThat(fromDb.get().getSource(), is(source));
    }

    @Test
    void importFlow_DryRun() {
        String oldSource = """
            id: import_dry
            namespace: some.namespace
            tasks:
            - id: task
              type: io.kestra.plugin.core.log.Log
              message: Hello""";
        Flow importFlow = flowService.importFlow("my-tenant", oldSource);

        assertThat(importFlow.getId(), is("import_dry"));
        assertThat(importFlow.getNamespace(), is("some.namespace"));
        assertThat(importFlow.getRevision(), is(1));
        assertThat(importFlow.getTasks().size(), is(1));
        assertThat(importFlow.getTasks().getFirst().getId(), is("task"));

        Optional<FlowWithSource> fromDb = flowRepository.findByIdWithSource("my-tenant", "some.namespace", "import_dry", Optional.empty());
        assertThat(fromDb.isPresent(), is(true));
        assertThat(fromDb.get().getRevision(), is(1));
        assertThat(fromDb.get().getSource(), is(oldSource));

        String newSource = oldSource.replace("id: task", "id: replaced_task");
        importFlow = flowService.importFlow("my-tenant", newSource, true);
        assertThat(importFlow.getRevision(), is(2));
        assertThat(importFlow.getTasks().size(), is(1));
        assertThat(importFlow.getTasks().getFirst().getId(), is("replaced_task"));

        fromDb = flowRepository.findByIdWithSource("my-tenant", "some.namespace", "import_dry", Optional.empty());
        assertThat(fromDb.isPresent(), is(true));
        assertThat(fromDb.get().getRevision(), is(1));
        assertThat(fromDb.get().getSource(), is(oldSource));
    }

    @Test
    void sameRevisionWithDeletedOrdered() {
        var flow1 = create("test", "test", 1);
        var flow2 = create("test", "test2", 2);
        var flow3 = create("test", "test2", 2).toDeleted();
        var flow4 = create("test", "test2", 4);
        Stream<FlowWithSource> stream = Stream.of(
            flow1.withSource(flow1.generateSource()),
            flow2.withSource(flow2.generateSource()),
            flow3.withSource(flow3.generateSource()),
            flow4.withSource(flow4.generateSource())
        );

        List<FlowWithSource> collect = flowService.keepLastVersion(stream).toList();

        assertThat(collect.size(), is(1));
        assertThat(collect.getFirst().isDeleted(), is(false));
        assertThat(collect.getFirst().getRevision(), is(4));
    }

    @Test
    void sameRevisionWithDeletedSameRevision() {
        var flow1 = create("test2", "test2", 1);
        var flow2 = create("test", "test", 1);
        var flow3 = create("test", "test2", 2);
        var flow4 = create("test", "test3", 3);
        var flow5 = create("test", "test2", 2).toDeleted();
        Stream<FlowWithSource> stream = Stream.of(
            flow1.withSource(flow1.generateSource()),
            flow2.withSource(flow2.generateSource()),
            flow3.withSource(flow3.generateSource()),
            flow4.withSource(flow4.generateSource()),
            flow5.withSource(flow5.generateSource())
        );

        List<FlowWithSource> collect = flowService.keepLastVersion(stream).toList();

        assertThat(collect.size(), is(1));
        assertThat(collect.getFirst().isDeleted(), is(false));
        assertThat(collect.getFirst().getId(), is("test2"));
    }

    @Test
    void sameRevisionWithDeletedUnordered() {
        var flow1 = create("test", "test", 1);
        var flow2 = create("test", "test2", 2);
        var flow3 = create("test", "test2", 4);
        var flow4 = create("test", "test2", 2).toDeleted();
        Stream<FlowWithSource> stream = Stream.of(
            flow1.withSource(flow1.generateSource()),
            flow2.withSource(flow2.generateSource()),
            flow3.withSource(flow3.generateSource()),
            flow4.withSource(flow4.generateSource())
        );

        List<FlowWithSource> collect = flowService.keepLastVersion(stream).toList();

        assertThat(collect.size(), is(1));
        assertThat(collect.getFirst().isDeleted(), is(false));
        assertThat(collect.getFirst().getRevision(), is(4));
    }

    @Test
    void multipleFlow() {
        var flow1 = create("test", "test", 2);
        var flow2 = create("test", "test2", 1);
        var flow3 = create("test2", "test2", 1);
        var flow4 = create("test2", "test3", 3);
        var flow5 = create("test3", "test1", 2);
        var flow6 = create("test3", "test2", 3);
        Stream<FlowWithSource> stream = Stream.of(
            flow1.withSource(flow1.generateSource()),
            flow2.withSource(flow2.generateSource()),
            flow3.withSource(flow3.generateSource()),
            flow4.withSource(flow4.generateSource()),
            flow5.withSource(flow5.generateSource()),
            flow6.withSource(flow6.generateSource())
        );

        List<FlowWithSource> collect = flowService.keepLastVersion(stream).toList();

        assertThat(collect.size(), is(3));
        assertThat(collect.stream().filter(flow -> flow.getId().equals("test")).findFirst().orElseThrow().getRevision(), is(2));
        assertThat(collect.stream().filter(flow -> flow.getId().equals("test2")).findFirst().orElseThrow().getRevision(), is(3));
        assertThat(collect.stream().filter(flow -> flow.getId().equals("test3")).findFirst().orElseThrow().getRevision(), is(3));
    }

    @Test
    void warnings() {
        Flow flow = create("test", "test", 1).toBuilder()
            .namespace("system")
            .triggers(List.of(
                io.kestra.plugin.core.trigger.Flow.builder()
                    .id("flow-trigger")
                    .type(io.kestra.plugin.core.trigger.Flow.class.getName())
                    .build()
            ))
            .build();

        List<String> warnings = flowService.warnings(flow);

        assertThat(warnings.size(), is(1));
        assertThat(warnings, containsInAnyOrder(
            "This flow will be triggered for EVERY execution of EVERY flow on your instance. We recommend adding the preconditions property to the Flow trigger 'flow-trigger'."
        ));
    }

    @Test
    void aliases() {
        List<FlowService.Relocation> warnings = flowService.relocations("""
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

        assertThat(warnings.size(), is(2));
        assertThat(warnings.getFirst().from(), is("io.kestra.core.runners.test.task.Alias"));
        assertThat(warnings.getFirst().to(), is("io.kestra.core.runners.test.TaskWithAlias"));
    }

    @SuppressWarnings("deprecation")
    @Test
    void propertyRenamingDeprecation() {
        Flow flow = Flow.builder()
            .id("flowId")
            .namespace("io.kestra.unittest")
            .inputs(List.of(
                StringInput.builder()
                    .id("inputWithId")
                    .type(Type.STRING)
                    .build(),
                StringInput.builder()
                    .name("inputWithName")
                    .type(Type.STRING)
                    .build()
            ))
            .tasks(Collections.singletonList(Echo.builder()
                .id("taskId")
                .type(Return.class.getName())
                .format(Property.of("test"))
                .build()))
            .build();

        assertThat(flowService.deprecationPaths(flow), containsInAnyOrder("inputs[1].name", "tasks[0]"));
    }

    @Test
    void isAllowedNamespace() {
        assertTrue(flowService.isAllowedNamespace("tenant", "namespace", "fromTenant", "fromNamespace"));
    }

    @Test
    void checkAllowedNamespace() {
        flowService.checkAllowedNamespace("tenant", "namespace", "fromTenant", "fromNamespace");
    }

    @Test
    void areAllowedAllNamespaces() {
        assertTrue(flowService.areAllowedAllNamespaces("tenant", "fromTenant", "fromNamespace"));
    }

    @Test
    void checkAllowedAllNamespaces() {
        flowService.checkAllowedAllNamespaces("tenant", "fromTenant", "fromNamespace");
    }

    @Test
    void delete() {
        Flow flow = create("deleteTest", "test", 1);
        FlowWithSource saved = flowRepository.create(flow, flow.generateSource(), flow);
        assertThat(flowRepository.findById(flow.getTenantId(), flow.getNamespace(), flow.getId()).isPresent(), is(true));
        flowService.delete(saved);
        assertThat(flowRepository.findById(flow.getTenantId(), flow.getNamespace(), flow.getId()).isPresent(), is(false));
    }

    @Test
    void findByNamespacePrefix() {
        Flow flow = create("findByTest", "test", 1).toBuilder().namespace("some.namespace").build();
        flowRepository.create(flow, flow.generateSource(), flow);
        assertThat(flowService.findByNamespacePrefix(null, "some.namespace").size(), is(1));
    }

    @Test
    void findById() {
        Flow flow = create("findByIdTest", "test", 1);
        FlowWithSource saved = flowRepository.create(flow, flow.generateSource(), flow);
        assertThat(flowService.findById(null, saved.getNamespace(), saved.getId()).isPresent(), is(true));
    }

    @Test
    void checkSubflowNotFound() {
        Flow flow = create("mainFlow", "task", 1).toBuilder()
            .tasks(List.of(
                io.kestra.plugin.core.flow.Subflow.builder()
                    .id("subflowTask")
                    .type(io.kestra.plugin.core.flow.Subflow.class.getName())
                    .namespace("io.kestra.unittest")
                    .flowId("nonExistentSubflow")
                    .build()
            ))
            .build();

        ConstraintViolationException exception = assertThrows(ConstraintViolationException.class, () -> {
            flowService.checkValidSubflows(flow);
        });

        assertThat(exception.getConstraintViolations().size(), is(1));
        assertThat(exception.getConstraintViolations().iterator().next().getMessage(), is("The subflow 'nonExistentSubflow' not found in namespace 'io.kestra.unittest'."));
    }

    @Test
    void checkValidSubflow() {
        Flow subflow = create("existingSubflow", "task", 1);
        flowRepository.create(subflow, subflow.generateSource(), subflow);

        Flow flow = create("mainFlow", "task", 1).toBuilder()
            .tasks(List.of(
                io.kestra.plugin.core.flow.Subflow.builder()
                    .id("subflowTask")
                    .type(io.kestra.plugin.core.flow.Subflow.class.getName())
                    .namespace("io.kestra.unittest")
                    .flowId("existingSubflow")
                    .build()
            ))
            .build();

        assertDoesNotThrow(() -> flowService.checkValidSubflows(flow));
    }
}