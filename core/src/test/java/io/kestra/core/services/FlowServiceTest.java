package io.kestra.core.services;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.check.Check;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.validations.ValidateConstraintViolation;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.plugin.core.debug.Echo;
import io.kestra.plugin.core.debug.Return;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
                .format(Property.ofValue("test"))
                .build()))
            .build();

        return flow.toBuilder().source(flow.sourceOrGenerateIfNull()).build();
    }

    @Test
    void shouldReturnTrueWhenValidatingFlowGivenDefaults() {
        // Given
        String source = """
            id: test
            namespace: io.kestra.unittest
            tasks:
              - id: download
                type: io.kestra.plugin.core.http.Download
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: This is a message
            pluginDefaults:
              - type: io.kestra.plugin.core
                values:
                  level: WARN
                  uri: https://kestra.io
            """;
        // When
        List<ValidateConstraintViolation> results = flowService.validate("my-tenant", source);

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.getFirst()).isEqualTo(new ValidateConstraintViolation("test", "io.kestra.unittest", 0, null, false, List.of(), List.of(), List.of()));
    }

    @Test
    void importFlow() throws FlowProcessingException {
        String source = """
            id: import
            namespace: some.namespace
            tasks:
            - id: task
              type: io.kestra.plugin.core.log.Log
              message: Hello""";
        FlowWithSource importFlow = flowService.importFlow("my-tenant", source);

        assertThat(importFlow.getId()).isEqualTo("import");
        assertThat(importFlow.getNamespace()).isEqualTo("some.namespace");
        assertThat(importFlow.getRevision()).isEqualTo(1);
        assertThat(importFlow.getTasks().size()).isEqualTo(1);
        assertThat(importFlow.getTasks().getFirst().getId()).isEqualTo("task");

        Optional<FlowWithSource> fromDb = flowRepository.findByIdWithSource("my-tenant", "some.namespace", "import", Optional.empty());
        assertThat(fromDb.isPresent()).isTrue();
        assertThat(fromDb.get().getRevision()).isEqualTo(1);
        assertThat(fromDb.get().getSource()).isEqualTo(source);

        source = source.replace("id: task", "id: replaced_task");
        importFlow = flowService.importFlow("my-tenant", source);
        assertThat(importFlow.getRevision()).isEqualTo(2);
        assertThat(importFlow.getTasks().size()).isEqualTo(1);
        assertThat(importFlow.getTasks().getFirst().getId()).isEqualTo("replaced_task");

        fromDb = flowRepository.findByIdWithSource("my-tenant", "some.namespace", "import", Optional.empty());
        assertThat(fromDb.isPresent()).isTrue();
        assertThat(fromDb.get().getRevision()).isEqualTo(2);
        assertThat(fromDb.get().getSource()).isEqualTo(source);
    }

    @Test
    void importFlow_DryRun() throws FlowProcessingException {
        String oldSource = """
            id: import_dry
            namespace: some.namespace
            tasks:
            - id: task
              type: io.kestra.plugin.core.log.Log
              message: Hello""";
        FlowWithSource importFlow = flowService.importFlow("my-tenant", oldSource);

        assertThat(importFlow.getId()).isEqualTo("import_dry");
        assertThat(importFlow.getNamespace()).isEqualTo("some.namespace");
        assertThat(importFlow.getRevision()).isEqualTo(1);
        assertThat(importFlow.getTasks().size()).isEqualTo(1);
        assertThat(importFlow.getTasks().getFirst().getId()).isEqualTo("task");

        Optional<FlowWithSource> fromDb = flowRepository.findByIdWithSource("my-tenant", "some.namespace", "import_dry", Optional.empty());
        assertThat(fromDb.isPresent()).isTrue();
        assertThat(fromDb.get().getRevision()).isEqualTo(1);
        assertThat(fromDb.get().getSource()).isEqualTo(oldSource);

        String newSource = oldSource.replace("id: task", "id: replaced_task");
        importFlow = flowService.importFlow("my-tenant", newSource, true);
        assertThat(importFlow.getRevision()).isEqualTo(2);
        assertThat(importFlow.getTasks().size()).isEqualTo(1);
        assertThat(importFlow.getTasks().getFirst().getId()).isEqualTo("replaced_task");

        fromDb = flowRepository.findByIdWithSource("my-tenant", "some.namespace", "import_dry", Optional.empty());
        assertThat(fromDb.isPresent()).isTrue();
        assertThat(fromDb.get().getRevision()).isEqualTo(1);
        assertThat(fromDb.get().getSource()).isEqualTo(oldSource);
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

        assertThat(collect.size()).isEqualTo(1);
        assertThat(collect.getFirst().isDeleted()).isFalse();
        assertThat(collect.getFirst().getRevision()).isEqualTo(4);
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

        assertThat(collect.size()).isEqualTo(1);
        assertThat(collect.getFirst().isDeleted()).isFalse();
        assertThat(collect.getFirst().getId()).isEqualTo("test2");
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

        assertThat(collect.size()).isEqualTo(1);
        assertThat(collect.getFirst().isDeleted()).isFalse();
        assertThat(collect.getFirst().getRevision()).isEqualTo(4);
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

        assertThat(collect.size()).isEqualTo(3);
        assertThat(collect.stream().filter(flow -> flow.getId().equals("test")).findFirst().orElseThrow().getRevision()).isEqualTo(2);
        assertThat(collect.stream().filter(flow -> flow.getId().equals("test2")).findFirst().orElseThrow().getRevision()).isEqualTo(3);
        assertThat(collect.stream().filter(flow -> flow.getId().equals("test3")).findFirst().orElseThrow().getRevision()).isEqualTo(3);
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

        assertThat(warnings.size()).isEqualTo(1);
        assertThat(warnings).containsExactlyInAnyOrder("This flow will be triggered for EVERY execution of EVERY flow on your instance. We recommend adding the preconditions property to the Flow trigger 'flow-trigger'.");
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

        assertThat(warnings.size()).isEqualTo(2);
        assertThat(warnings.getFirst().from()).isEqualTo("io.kestra.core.runners.test.task.Alias");
        assertThat(warnings.getFirst().to()).isEqualTo("io.kestra.core.runners.test.TaskWithAlias");
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
                .format(Property.ofValue("test"))
                .build()))
            .build();

        assertThat(flowService.deprecationPaths(flow)).containsExactlyInAnyOrder("inputs[1].name", "tasks[0]");
    }

    @Test
    void delete() {
        FlowWithSource flow = create("deleteTest", "test", 1);
        FlowWithSource saved = flowRepository.create(GenericFlow.of(flow));
        assertThat(flowRepository.findById(flow.getTenantId(), flow.getNamespace(), flow.getId()).isPresent()).isTrue();
        flowService.delete(saved);
        assertThat(flowRepository.findById(flow.getTenantId(), flow.getNamespace(), flow.getId()).isPresent()).isFalse();
    }

    @Test
    void findByNamespacePrefix() {
        FlowWithSource flow = create(null, "some.namespace","findByTest", "test", 1);
        flowRepository.create(GenericFlow.of(flow));
        assertThat(flowService.findByNamespacePrefix(null, "some.namespace").size()).isEqualTo(1);
    }

    @Test
    void findById() {
        FlowWithSource flow = create("findByIdTest", "test", 1);
        FlowWithSource saved = flowRepository.create(GenericFlow.of(flow));
        assertThat(flowService.findById(null, saved.getNamespace(), saved.getId()).isPresent()).isTrue();
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

        assertThat(exceptions.size()).isEqualTo(1);
        assertThat(exceptions.iterator().next()).isEqualTo("The subflow 'nonExistentSubflow' not found in namespace 'io.kestra.unittest'.");
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

        assertThat(exceptions.size()).isZero();
    }

    @Test
    void shouldReturnValidationForRunnablePropsOnFlowable() {
        // Given
        String source = """
            id: dolphin_164914
            namespace: company.team

            tasks:
              - id: for
                type: io.kestra.plugin.core.flow.ForEach
                values: [1, 2, 3]
                workerGroup:
                  key: toto
                timeout: PT10S
                taskCache:
                  enabled: true
                tasks:
                - id: hello
                  type: io.kestra.plugin.core.log.Log
                  message: Hello World! 🚀
                  workerGroup:
                    key: toto
                  timeout: PT10S
                  taskCache:
                    enabled: true
            """;

        // When
        List<ValidateConstraintViolation> results = flowService.validate("my-tenant", source);

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getWarnings()).hasSize(3);
        assertThat(results.getFirst().getWarnings()).containsExactlyInAnyOrder(
            "The task 'for' cannot use the 'timeout' property as it's only relevant for runnable tasks.",
            "The task 'for' cannot use the 'taskCache' property as it's only relevant for runnable tasks.",
            "The task 'for' cannot use the 'workerGroup' property as it's only relevant for runnable tasks."
        );
    }

    @Test
    void shouldReturnValidationErrorForReservedFlowId() {
        // Given
        String source = """
        id: pause
        namespace: io.kestra.unittest
        tasks:
          - id: task
            type: io.kestra.plugin.core.log.Log
            message: Reserved id test
        """;
        // When
        List<ValidateConstraintViolation> results = flowService.validate("my-tenant", source);

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getConstraints()).contains("Flow id is a reserved keyword: pause");
    }

    @Test
    void shouldReturnEmptyListGivenFlowWithNoChecks() {
        // Given
        Flow flow = mock(Flow.class);
        when(flow.getChecks()).thenReturn(List.of());

        // When
        List<Check> result = flowService.getFailedChecks(flow, Map.of());

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnCheckWhenConditionEvaluatesFalse() {
        // Given
        Check failingCheck = Check.builder()
            .condition("{{ false }}")
            .message("fail")
            .behavior(Check.Behavior.FAIL_EXECUTION)
            .build();
        Flow flow = mock(Flow.class);
        when(flow.getChecks()).thenReturn(List.of(failingCheck));
        when(flow.getNamespace()).thenReturn("io.kestra.unittest");
        when(flow.getId()).thenReturn("test");

        // When
        List<Check> result = flowService.getFailedChecks(flow, Map.of());

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).isEqualTo(failingCheck);
    }

    @Test
    void shouldReturnEmptyListWhenConditionEvaluatesTrue() {
        // Given
        Check passingCheck = Check.builder()
            .condition("{{ true }}")
            .message("pass")
            .behavior(Check.Behavior.FAIL_EXECUTION)
            .build();
        Flow flow = mock(Flow.class);
        when(flow.getChecks()).thenReturn(List.of(passingCheck));
        when(flow.getNamespace()).thenReturn("io.kestra.unittest");
        when(flow.getId()).thenReturn("test");

        // When
        List<Check> result = flowService.getFailedChecks(flow, Map.of());

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnCheckWithErrorMessageWhenExceptionThrown() {
        // Given
        Check check = Check.builder()
            .condition("{{ invalidFunction() }}")
            .message("ignored")
            .behavior(Check.Behavior.FAIL_EXECUTION)
            .build();
        Flow flow = mock(Flow.class);
        when(flow.getChecks()).thenReturn(List.of(check));
        when(flow.getNamespace()).thenReturn("io.kestra.unittest");
        when(flow.getId()).thenReturn("test");

        // When
        List<Check> result = flowService.getFailedChecks(flow, Map.of());

        // Then
        assertThat(result).hasSize(1);
        Check errorCheck = result.getFirst();
        assertThat(errorCheck.getBehavior()).isEqualTo(Check.Behavior.BLOCK_EXECUTION);
        assertThat(errorCheck.getStyle()).isEqualTo(Check.Style.ERROR);
        assertThat(errorCheck.getMessage()).contains("Failed to evaluate check condition. Cause:");
    }

    @Test
    void shouldHandleMultipleChecksWithMixedResults() {
        // Given
        Check passCheck = Check.builder().condition("{{ true }}").message("pass").build();
        Check failCheck = Check.builder().condition("{{ false }}").message("fail").build();
        Check exceptionCheck = Check.builder().condition("{{ invalidFunction }}").message("exception").build();

        Flow flow = mock(Flow.class);
        when(flow.getChecks()).thenReturn(List.of(passCheck, failCheck, exceptionCheck));
        when(flow.getNamespace()).thenReturn("io.kestra.unittest");
        when(flow.getId()).thenReturn("test");

        // When
        List<Check> result = flowService.getFailedChecks(flow, Map.of());

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).contains(failCheck);
        assertThat(result)
            .anyMatch(c -> c.getMessage().contains("Failed to evaluate check condition") &&
                c.getBehavior() == Check.Behavior.BLOCK_EXECUTION &&
                c.getStyle() == Check.Style.ERROR);
    }

    @Test
    void shouldAcceptExpressionWithFlowWhenRenderingChecks() {
        // Given
        Check passCheck = Check.builder().condition("{{ flow.id == 'test' }}").message("pass").build();

        Flow flow = mock(Flow.class);
        when(flow.getChecks()).thenReturn(List.of(passCheck));
        when(flow.getNamespace()).thenReturn("io.kestra.unittest");
        when(flow.getId()).thenReturn("test");

        // When
        List<Check> result = flowService.getFailedChecks(flow, Map.of());

        // Then
        assertThat(result).isEmpty();
    }
}