package io.kestra.core.services;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.property.Property;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.plugin.core.debug.Echo;
import io.kestra.plugin.core.debug.Return;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest
class FlowServiceTest {
    private static final String TEST_NAMESPACE = "io.kestra.unittest";

    @Inject
    private FlowService flowService;
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
            .tasks(Collections.singletonList(Return.builder()
                .id(taskId)
                .type(Return.class.getName())
                .format(Property.of("test"))
                .build()))
            .build();

        return flow.toBuilder().source(flow.sourceOrGenerateIfNull()).build();
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
        FlowWithSource importFlow = flowService.importFlow("my-tenant", source);

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
        FlowWithSource importFlow = flowService.importFlow("my-tenant", oldSource);

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
        Stream<FlowInterface> stream = Stream.of(
            create("test", "test", 1),
            create("test", "test2", 2),
            create("test", "test2", 2).toDeleted(),
            create("test", "test2", 4)
        );

        List<FlowInterface> collect = flowService.keepLastVersion(stream).toList();

        assertThat(collect.size(), is(1));
        assertThat(collect.getFirst().isDeleted(), is(false));
        assertThat(collect.getFirst().getRevision(), is(4));
    }

    @Test
    void sameRevisionWithDeletedSameRevision() {

        Stream<FlowInterface> stream = Stream.of(
            create("test2", "test2", 1),
            create("test", "test", 1),
            create("test", "test2", 2),
            create("test", "test3", 3),
            create("test", "test2", 2).toDeleted()
        );

        List<FlowInterface> collect = flowService.keepLastVersion(stream).toList();

        assertThat(collect.size(), is(1));
        assertThat(collect.getFirst().isDeleted(), is(false));
        assertThat(collect.getFirst().getId(), is("test2"));
    }

    @Test
    void sameRevisionWithDeletedUnordered() {

        Stream<FlowInterface> stream = Stream.of(
            create("test", "test", 1),
            create("test", "test2", 2),
            create("test", "test2", 4),
            create("test", "test2", 2).toDeleted()
        );

        List<FlowInterface> collect = flowService.keepLastVersion(stream).toList();

        assertThat(collect.size(), is(1));
        assertThat(collect.getFirst().isDeleted(), is(false));
        assertThat(collect.getFirst().getRevision(), is(4));
    }

    @Test
    void multipleFlow() {

        Stream<FlowInterface> stream = Stream.of(
            create("test", "test", 2),
            create("test", "test2", 1),
            create("test2", "test2", 1),
            create("test2", "test3", 3),
            create("test3", "test1", 2),
            create("test3", "test2", 3)
        );

        List<FlowInterface> collect = flowService.keepLastVersion(stream).toList();

        assertThat(collect.size(), is(3));
        assertThat(collect.stream().filter(flow -> flow.getId().equals("test")).findFirst().orElseThrow().getRevision(), is(2));
        assertThat(collect.stream().filter(flow -> flow.getId().equals("test2")).findFirst().orElseThrow().getRevision(), is(3));
        assertThat(collect.stream().filter(flow -> flow.getId().equals("test3")).findFirst().orElseThrow().getRevision(), is(3));
    }

    @Test
    void warnings() {
        FlowWithSource flow = create("test", "test", 1).toBuilder()
            .namespace("system")
            .triggers(List.of(
                io.kestra.plugin.core.trigger.Flow.builder()
                    .id("flow-trigger")
                    .type(io.kestra.plugin.core.trigger.Flow.class.getName())
                    .build()
            ))
            .build();

        List<String> warnings = flowService.warnings(flow, null);

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
        FlowWithSource flow = FlowWithSource.builder()
            .id("flowId")
            .namespace(TEST_NAMESPACE)
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
        FlowWithSource flow = create("deleteTest", "test", 1);
        FlowWithSource saved = flowRepository.create(GenericFlow.of(flow));
        assertThat(flowRepository.findById(flow.getTenantId(), flow.getNamespace(), flow.getId()).isPresent(), is(true));
        flowService.delete(saved);
        assertThat(flowRepository.findById(flow.getTenantId(), flow.getNamespace(), flow.getId()).isPresent(), is(false));
    }

    @Test
    void findByNamespacePrefix() {
        FlowWithSource flow = create(null, "some.namespace","findByTest", "test", 1);
        flowRepository.create(GenericFlow.of(flow));
        assertThat(flowService.findByNamespacePrefix(null, "some.namespace").size(), is(1));
    }

    @Test
    void findById() {
        FlowWithSource flow = create("findByIdTest", "test", 1);
        FlowWithSource saved = flowRepository.create(GenericFlow.of(flow));
        assertThat(flowService.findById(null, saved.getNamespace(), saved.getId()).isPresent(), is(true));
    }

    @Test
    void checkSubflowNotFound() {
        FlowWithSource flow = create("mainFlow", "task", 1).toBuilder()
            .tasks(List.of(
                io.kestra.plugin.core.flow.Subflow.builder()
                    .id("subflowTask")
                    .type(io.kestra.plugin.core.flow.Subflow.class.getName())
                    .namespace(TEST_NAMESPACE)
                    .flowId("nonExistentSubflow")
                    .build()
            ))
            .build();

        List<String> exceptions = flowService.checkValidSubflows(flow, null);

        assertThat(exceptions.size(), is(1));
        assertThat(exceptions.iterator().next(), is("The subflow 'nonExistentSubflow' not found in namespace 'io.kestra.unittest'."));
    }

    @Test
    void checkValidSubflow() {
        FlowWithSource subflow = create("existingSubflow", "task", 1);
        flowRepository.create(GenericFlow.of(subflow));

        FlowWithSource flow = create("mainFlow", "task", 1).toBuilder()
            .tasks(List.of(
                io.kestra.plugin.core.flow.Subflow.builder()
                    .id("subflowTask")
                    .type(io.kestra.plugin.core.flow.Subflow.class.getName())
                    .namespace(TEST_NAMESPACE)
                    .flowId("existingSubflow")
                    .build()
            ))
            .build();

        List<String> exceptions = flowService.checkValidSubflows(flow, null);

        assertThat(exceptions.size(), is(0));
    }
}