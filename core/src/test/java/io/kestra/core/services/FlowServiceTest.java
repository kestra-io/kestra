package io.kestra.core.services;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.*;
import io.kestra.core.models.flows.check.Check;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.models.validations.ValidateConstraintViolation;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.FlowTopologyRepositoryInterface;
import io.kestra.core.scheduler.queue.TriggerEventQueue;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.flow.Subflow;
import io.kestra.plugin.core.trigger.Schedule;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@KestraTest
@Execution(ExecutionMode.SAME_THREAD)
class FlowServiceTest {
    private static final String TEST_NAMESPACE = "io.kestra.unittest";

    @Inject
    private FlowService flowService;
    @Inject
    private FlowRepositoryInterface flowRepository;
    @Inject
    private FlowTopologyRepositoryInterface flowTopologyRepository;
    @Inject
    private BroadcastQueueInterface<FlowInterface> flowQueue;
    @Inject
    private TriggerEventQueue triggerEventQueue;

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
        List<ValidateConstraintViolation> results = flowService.validate("my-tenant", List.of(new FlowSource(null, source)));

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.getFirst()).isEqualTo(new ValidateConstraintViolation(0, null, "io.kestra.unittest", "test", null, false, List.of(), List.of(), List.of()));
    }

    @Test
    void shouldReturnTrueWhenValidatingFlowWithFilenameGivenDefaults() {
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
        List<ValidateConstraintViolation> results = flowService.validate("my-tenant", List.of(new FlowSource("flow.yaml", source)));

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.getFirst()).isEqualTo(new ValidateConstraintViolation(0, "flow.yaml", "io.kestra.unittest", "test", null, false, List.of(), List.of(), List.of()));
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
    void findByNamespacePrefix() {
        FlowWithSource exactMatch = create(null, "prefix.namespace", "prefixExact", "test", 1);
        flowRepository.create(GenericFlow.of(exactMatch));

        FlowWithSource childMatch = create(null, "prefix.namespace.child", "prefixChild", "test", 1);
        flowRepository.create(GenericFlow.of(childMatch));

        FlowWithSource similarPrefix = create(null, "prefix.namespace2", "prefixSimilar", "test", 1);
        flowRepository.create(GenericFlow.of(similarPrefix));

        FlowWithSource differentNs = create(null, "other.namespace", "prefixOther", "test", 1);
        flowRepository.create(GenericFlow.of(differentNs));

        List<Flow> results = flowService.findByNamespacePrefix(null, "prefix.namespace");

        assertThat(results)
            .hasSize(2)
            .extracting(Flow::getId)
            .containsExactlyInAnyOrder("prefixExact", "prefixChild");
    }

    @Test
    void findById() {
        FlowWithSource flow = create("findByIdTest", "test", 1);
        FlowWithSource saved = flowRepository.create(GenericFlow.of(flow));
        assertThat(flowService.findById(null, saved.getNamespace(), saved.getId()).isPresent()).isTrue();
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
        List<ValidateConstraintViolation> results = flowService.validate("my-tenant", List.of(new FlowSource(null, source)));

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
        List<ValidateConstraintViolation> results = flowService.validate("my-tenant", List.of(new FlowSource(null, source)));

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
            .anyMatch(
                c -> c.getMessage().contains("Failed to evaluate check condition") &&
                    c.getBehavior() == Check.Behavior.BLOCK_EXECUTION &&
                    c.getStyle() == Check.Style.ERROR
            );
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

    @Test
    void create() throws FlowProcessingException, QueueException, InterruptedException {
        Flow subflow = Flow.builder()
            .id(IdUtils.create())
            .tenantId(TenantService.MAIN_TENANT)
            .namespace("io.kestra.unittest")
            .tasks(List.of(Return.builder().id("test").type(Return.class.getName()).format(Property.ofValue("test")).build()))
            .build();
        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .tenantId(TenantService.MAIN_TENANT)
            .namespace("io.kestra.unittest")
            .tasks(List.of(Subflow.builder().id("test").type(Subflow.class.getName()).namespace("io.kestra.unittest").flowId(subflow.getId()).build()))
            .triggers(List.of(Schedule.builder().id("test").type(Schedule.class.getName()).cron("0 0 * * *").build()))
            .build();

        CountDownLatch countDownLatch = new CountDownLatch(1);
        flowQueue.addListener(f ->
        {
            if (f.getId().equals(flow.getId())) {
                countDownLatch.countDown();
            }
        });

        flowService.create(GenericFlow.of(subflow));
        flowService.create(GenericFlow.of(flow));

        // check that it has been created
        Optional<FlowWithSource> fromDb = flowRepository.findByIdWithSource(flow.getTenantId(), flow.getNamespace(), flow.getId(), Optional.empty());
        assertThat(fromDb).isPresent();
        assertThat(fromDb.get().getRevision()).isEqualTo(1);

        // check that topology has been inserted
        List<FlowTopology> topo = await()
            .atMost(Duration.ofSeconds(10))
            .until(
                () -> flowTopologyRepository.findByFlow(flow.getTenantId(), flow.getNamespace(), flow.getId(), false),
                it -> !it.isEmpty()
            );
        assertThat(topo).hasSize(1);
        assertThat(topo.getFirst().getSource().getId()).isEqualTo(flow.getId());
        assertThat(topo.getFirst().getDestination().getId()).isEqualTo(subflow.getId());

        // check that triggers have been sent
        verify(triggerEventQueue).send(any());

        // check that the flow has been sent to the queue
        assertTrue(countDownLatch.await(10, TimeUnit.SECONDS));
    }

    @Test
    void update() throws FlowProcessingException, QueueException, InterruptedException {
        Flow subflow = Flow.builder()
            .id(IdUtils.create())
            .tenantId(TenantService.MAIN_TENANT)
            .namespace("io.kestra.unittest")
            .tasks(List.of(Return.builder().id("test").type(Return.class.getName()).format(Property.ofValue("test")).build()))
            .build();
        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .tenantId(TenantService.MAIN_TENANT)
            .namespace("io.kestra.unittest")
            .tasks(List.of(Return.builder().id("test").type(Return.class.getName()).format(Property.ofValue("test")).build()))
            .build();

        CountDownLatch countDownLatch = new CountDownLatch(2);
        flowQueue.addListener(f ->
        {
            if (f.getId().equals(flow.getId())) {
                countDownLatch.countDown();
            }
        });

        flowService.create(GenericFlow.of(subflow));
        flowService.create(GenericFlow.of(flow));
        Flow updated = flow.toBuilder()
            .tasks(List.of(Subflow.builder().id("test").type(Subflow.class.getName()).namespace("io.kestra.unittest").flowId(subflow.getId()).build()))
            .triggers(List.of(Schedule.builder().id("test").type(Schedule.class.getName()).cron("0 0 * * *").build()))
            .build();
        flowService.update(GenericFlow.of(updated), GenericFlow.of(flow));

        // check that it has been created then updated
        Optional<FlowWithSource> fromDb = flowRepository.findByIdWithSource(flow.getTenantId(), flow.getNamespace(), flow.getId(), Optional.empty());
        assertThat(fromDb).isPresent();
        assertThat(fromDb.get().getRevision()).isEqualTo(2);

        // check that topology has been inserted
        List<FlowTopology> topo = await()
            .atMost(Duration.ofSeconds(10))
            .until(
                () -> flowTopologyRepository.findByFlow(flow.getTenantId(), flow.getNamespace(), flow.getId(), false),
                it -> !it.isEmpty()
            );
        assertThat(topo).hasSize(1);
        assertThat(topo.getFirst().getSource().getId()).isEqualTo(flow.getId());
        assertThat(topo.getFirst().getDestination().getId()).isEqualTo(subflow.getId());

        // check that triggers have been sent
        verify(triggerEventQueue).send(any());

        // check that the flow has been sent to the queue 2x
        assertTrue(countDownLatch.await(10, TimeUnit.SECONDS));
    }

    @Test
    void delete() throws FlowProcessingException, QueueException, InterruptedException {
        Flow subflow = Flow.builder()
            .id(IdUtils.create())
            .tenantId(TenantService.MAIN_TENANT)
            .namespace("io.kestra.unittest")
            .tasks(List.of(Return.builder().id("test").type(Return.class.getName()).format(Property.ofValue("test")).build()))
            .build();
        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .tenantId(TenantService.MAIN_TENANT)
            .namespace("io.kestra.unittest")
            .tasks(List.of(Subflow.builder().id("test").type(Subflow.class.getName()).namespace("io.kestra.unittest").flowId(subflow.getId()).build()))
            .triggers(List.of(Schedule.builder().id("test").type(Schedule.class.getName()).cron("0 0 * * *").build()))
            .build();

        CountDownLatch countDownLatch = new CountDownLatch(2);
        flowQueue.addListener(f ->
        {
            if (f.getId().equals(flow.getId())) {
                countDownLatch.countDown();
            }
        });

        flowService.create(GenericFlow.of(subflow));
        FlowWithSource created = flowService.create(GenericFlow.of(flow));

        // be sure that topology and triggers have been computed
        await()
            .atMost(Duration.ofSeconds(10))
            .until(() -> !flowTopologyRepository.findByFlow(flow.getTenantId(), flow.getNamespace(), flow.getId(), false).isEmpty());
        verify(triggerEventQueue).send(any());
        reset(triggerEventQueue);

        flowService.delete(created);

        // check that it has been deleted
        Optional<FlowWithSource> fromDb = flowRepository.findByIdWithSource(flow.getTenantId(), flow.getNamespace(), flow.getId(), Optional.empty());
        assertThat(fromDb).isEmpty();

        // check that topology has been removed
        await()
            .atMost(Duration.ofSeconds(10))
            .until(() -> flowTopologyRepository.findByFlow(flow.getTenantId(), flow.getNamespace(), flow.getId(), false).isEmpty());

        // check that triggers have been removed
        verify(triggerEventQueue).send(any());

        // check that the flow has been sent to the queue 2x
        assertTrue(countDownLatch.await(10, TimeUnit.SECONDS));
    }

    @MockBean
    @Replaces(TriggerEventQueue.class)
    TriggerEventQueue triggerEventQueue() {
        return mock(TriggerEventQueue.class);
    }
}