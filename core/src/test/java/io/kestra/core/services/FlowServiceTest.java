package io.kestra.core.services;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.plugin.core.debug.Echo;
import io.kestra.plugin.core.debug.Return;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
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
                .format("test")
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
        assertThat(importFlow.getTasks().get(0).getId(), is("task"));

        Optional<FlowWithSource> fromDb = flowRepository.findByIdWithSource("my-tenant", "some.namespace", "import", Optional.empty());
        assertThat(fromDb.isPresent(), is(true));
        assertThat(fromDb.get().getRevision(), is(1));
        assertThat(fromDb.get().getSource(), is(source));

        source = source.replace("id: task", "id: replaced_task");
        importFlow = flowService.importFlow("my-tenant", source);
        assertThat(importFlow.getRevision(), is(2));
        assertThat(importFlow.getTasks().size(), is(1));
        assertThat(importFlow.getTasks().get(0).getId(), is("replaced_task"));

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
        assertThat(importFlow.getTasks().get(0).getId(), is("task"));

        Optional<FlowWithSource> fromDb = flowRepository.findByIdWithSource("my-tenant", "some.namespace", "import_dry", Optional.empty());
        assertThat(fromDb.isPresent(), is(true));
        assertThat(fromDb.get().getRevision(), is(1));
        assertThat(fromDb.get().getSource(), is(oldSource));

        String newSource = oldSource.replace("id: task", "id: replaced_task");
        importFlow = flowService.importFlow("my-tenant", newSource, true);
        assertThat(importFlow.getRevision(), is(2));
        assertThat(importFlow.getTasks().size(), is(1));
        assertThat(importFlow.getTasks().get(0).getId(), is("replaced_task"));

        fromDb = flowRepository.findByIdWithSource("my-tenant", "some.namespace", "import_dry", Optional.empty());
        assertThat(fromDb.isPresent(), is(true));
        assertThat(fromDb.get().getRevision(), is(1));
        assertThat(fromDb.get().getSource(), is(oldSource));
    }

    @Test
    void sameRevisionWithDeletedOrdered() {
        Stream<Flow> stream = Stream.of(
            create("test", "test", 1),
            create("test", "test2", 2),
            create("test", "test2", 2).toDeleted(),
            create("test", "test2", 4)
        );

        List<Flow> collect = flowService.keepLastVersion(stream).collect(Collectors.toList());

        assertThat(collect.size(), is(1));
        assertThat(collect.get(0).isDeleted(), is(false));
        assertThat(collect.get(0).getRevision(), is(4));
    }

    @Test
    void sameRevisionWithDeletedSameRevision() {
        Stream<Flow> stream = Stream.of(
            create("test2", "test2", 1),
            create("test", "test", 1),
            create("test", "test2", 2),
            create("test", "test3", 3),
            create("test", "test2", 2).toDeleted()
        );

        List<Flow> collect = flowService.keepLastVersion(stream).collect(Collectors.toList());

        assertThat(collect.size(), is(1));
        assertThat(collect.get(0).isDeleted(), is(false));
        assertThat(collect.get(0).getId(), is("test2"));
    }

    @Test
    void sameRevisionWithDeletedUnordered() {
        Stream<Flow> stream = Stream.of(
            create("test", "test", 1),
            create("test", "test2", 2),
            create("test", "test2", 4),
            create("test", "test2", 2).toDeleted()
        );

        List<Flow> collect = flowService.keepLastVersion(stream).collect(Collectors.toList());

        assertThat(collect.size(), is(1));
        assertThat(collect.get(0).isDeleted(), is(false));
        assertThat(collect.get(0).getRevision(), is(4));
    }

    @Test
    void multipleFlow() {
        Stream<Flow> stream = Stream.of(
            create("test", "test", 2),
            create("test", "test2", 1),
            create("test2", "test2", 1),
            create("test2", "test3", 3),
            create("test3", "test1", 2),
            create("test3", "test2", 3)

        );

        List<Flow> collect = flowService.keepLastVersion(stream).collect(Collectors.toList());

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

        assertThat(warnings.size(), is(2));
        assertThat(warnings, containsInAnyOrder(
            "The system namespace is reserved for background workflows intended to perform routine tasks such as sending alerts and purging logs. Please use another namespace name.",
            "This flow will be triggered for EVERY execution of EVERY flow on your instance. We recommend adding the conditions property to the Flow trigger."
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
                type: io.kestra.plugin.core.flow.EachSequential
                value:\s
                  - 1
                  - 2
                  - 3
                tasks:
                  - id: log-alias-each
                    type: io.kestra.core.runners.test.task.Alias
                    message: Hello, {{taskrun.value}}""");

        assertThat(warnings.size(), is(2));
        assertThat(warnings.get(0).from(), is("io.kestra.core.runners.test.task.Alias"));
        assertThat(warnings.get(0).to(), is("io.kestra.core.runners.test.TaskWithAlias"));
    }

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
                .format("test")
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
}