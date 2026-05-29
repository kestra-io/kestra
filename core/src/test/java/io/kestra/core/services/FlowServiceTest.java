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

import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.scheduler.events.TriggerCreated;
import io.kestra.core.scheduler.events.TriggerEvent;
import io.kestra.core.scheduler.events.TriggerFlowRevisionUpdated;
import io.kestra.core.scheduler.events.TriggerUpdated;

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
    void shouldReturnNoWarningsWhenPropertiesProvidedByPluginDefaults() {
        // Given
        String source = """
            id: test
            namespace: io.kestra.unittest
            tasks:
              - id: download
                type: io.kestra.plugin.core.http.Download
            pluginDefaults:
              - type: io.kestra.plugin.core.http.Download
                values:
                  uri: https://kestra.io
            """;

        // When
        List<ValidateConstraintViolation> results = flowService.validate("my-tenant", List.of(new FlowSource(null, source)));

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getConstraints()).isNull();
        assertThat(results.getFirst().getWarnings()).isEmpty();
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
                type: io.kestra.plugin.core.flow.Loop
                values: [1, 2, 3]
                workerSelector:
                  tags:
                    - toto
                timeout: PT10S
                taskCache:
                  enabled: true
                tasks:
                - id: hello
                  type: io.kestra.plugin.core.log.Log
                  message: Hello World! 🚀
                  workerSelector:
                    tags:
                      - toto
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
            "The task 'for' cannot use the 'workerSelector' property as it's only relevant for runnable tasks."
        );
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
            .when("{{ false }}")
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
            .when("{{ true }}")
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
            .when("{{ invalidFunction() }}")
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
        Check passCheck = Check.builder().when("{{ true }}").message("pass").build();
        Check failCheck = Check.builder().when("{{ false }}").message("fail").build();
        Check exceptionCheck = Check.builder().when("{{ invalidFunction }}").message("exception").build();

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
        Check passCheck = Check.builder().when("{{ flow.id == 'test' }}").message("pass").build();

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

    @Test
    void shouldFindUnchangedTriggersGivenIdenticalTriggersInBothRevisions() {
        // Given
        Schedule trigger = Schedule.builder().id("schedule").type(Schedule.class.getName()).cron("0 0 * * *").build();
        Flow current = Flow.builder()
            .id("test").namespace(TEST_NAMESPACE).revision(2)
            .tasks(List.of(Return.builder().id("task").type(Return.class.getName()).format(Property.ofValue("updated")).build()))
            .triggers(List.of(trigger))
            .build();
        Flow previous = Flow.builder()
            .id("test").namespace(TEST_NAMESPACE).revision(1)
            .tasks(List.of(Return.builder().id("task").type(Return.class.getName()).format(Property.ofValue("original")).build()))
            .triggers(List.of(trigger))
            .build();

        // When
        List<AbstractTrigger> unchanged = FlowService.findUnchangedTrigger(current, previous);

        // Then
        assertThat(unchanged).hasSize(1);
        assertThat(unchanged.getFirst().getId()).isEqualTo("schedule");
    }

    @Test
    void shouldReturnEmptyWhenFindUnchangedTriggersGivenModifiedTrigger() {
        // Given
        Schedule currentTrigger = Schedule.builder().id("schedule").type(Schedule.class.getName()).cron("*/5 * * * *").build();
        Schedule previousTrigger = Schedule.builder().id("schedule").type(Schedule.class.getName()).cron("0 0 * * *").build();
        Flow current = Flow.builder()
            .id("test").namespace(TEST_NAMESPACE).revision(2)
            .tasks(List.of(Return.builder().id("task").type(Return.class.getName()).format(Property.ofValue("test")).build()))
            .triggers(List.of(currentTrigger))
            .build();
        Flow previous = Flow.builder()
            .id("test").namespace(TEST_NAMESPACE).revision(1)
            .tasks(List.of(Return.builder().id("task").type(Return.class.getName()).format(Property.ofValue("test")).build()))
            .triggers(List.of(previousTrigger))
            .build();

        // When
        List<AbstractTrigger> unchanged = FlowService.findUnchangedTrigger(current, previous);

        // Then
        assertThat(unchanged).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenFindUnchangedTriggersGivenNewTrigger() {
        // Given
        Schedule trigger = Schedule.builder().id("new-schedule").type(Schedule.class.getName()).cron("0 0 * * *").build();
        Flow current = Flow.builder()
            .id("test").namespace(TEST_NAMESPACE).revision(2)
            .tasks(List.of(Return.builder().id("task").type(Return.class.getName()).format(Property.ofValue("test")).build()))
            .triggers(List.of(trigger))
            .build();
        Flow previous = Flow.builder()
            .id("test").namespace(TEST_NAMESPACE).revision(1)
            .tasks(List.of(Return.builder().id("task").type(Return.class.getName()).format(Property.ofValue("test")).build()))
            .build();

        // When
        List<AbstractTrigger> unchanged = FlowService.findUnchangedTrigger(current, previous);

        // Then
        assertThat(unchanged).isEmpty();
    }

    @Test
    void shouldEmitTriggerFlowRevisionUpdatedForUnchangedTriggersWhenFlowTasksChange() throws FlowProcessingException, QueueException {
        // Given — a flow with a trigger
        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .tenantId(TenantService.MAIN_TENANT)
            .namespace(TEST_NAMESPACE)
            .tasks(List.of(Return.builder().id("task").type(Return.class.getName()).format(Property.ofValue("original")).build()))
            .triggers(List.of(Schedule.builder().id("schedule").type(Schedule.class.getName()).cron("0 0 * * *").build()))
            .build();
        flowService.create(GenericFlow.of(flow));
        reset(triggerEventQueue);

        // When — update only the task (trigger is unchanged)
        Flow updated = flow.toBuilder()
            .tasks(List.of(Return.builder().id("task").type(Return.class.getName()).format(Property.ofValue("updated")).build()))
            .build();
        flowService.update(GenericFlow.of(updated), GenericFlow.of(flow));

        // Then — a TriggerFlowRevisionUpdated event is emitted for the unchanged trigger (to refresh cache)
        var captor = org.mockito.ArgumentCaptor.forClass(TriggerEvent.class);
        verify(triggerEventQueue).send(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(TriggerFlowRevisionUpdated.class);
        assertThat(captor.getValue().id().getTriggerId()).isEqualTo("schedule");
    }

    @Test
    void shouldEmitTriggerCreatedWhenRecreatingFlowAfterSoftDelete() throws FlowProcessingException, QueueException {
        // Given — a flow with a trigger, then soft-deleted
        String flowId = IdUtils.create();
        Flow flow = Flow.builder()
            .id(flowId)
            .tenantId(TenantService.MAIN_TENANT)
            .namespace(TEST_NAMESPACE)
            .tasks(List.of(Return.builder().id("task").type(Return.class.getName()).format(Property.ofValue("test")).build()))
            .triggers(List.of(Schedule.builder().id("schedule").type(Schedule.class.getName()).cron("0 0 * * *").build()))
            .build();
        FlowWithSource created = flowService.create(GenericFlow.of(flow));
        flowService.delete(created);
        reset(triggerEventQueue);

        // When — re-create a flow with the same id (trigger definition unchanged)
        Flow recreated = flow.toBuilder()
            .tasks(List.of(Return.builder().id("task").type(Return.class.getName()).format(Property.ofValue("revived")).build()))
            .build();
        flowService.create(GenericFlow.of(recreated));

        // Then — a TriggerCreated event is emitted, since the scheduler's trigger state
        // was dropped on the previous TriggerDeleted and must be rebuilt from scratch.
        var captor = org.mockito.ArgumentCaptor.forClass(TriggerEvent.class);
        verify(triggerEventQueue).send(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(TriggerCreated.class);
        assertThat(captor.getValue().id().getTriggerId()).isEqualTo("schedule");
    }

    @Test
    void shouldEmitTriggerCreatedWhenAddingNewTriggerToExistingFlow() throws FlowProcessingException, QueueException {
        // Given — a flow with no triggers
        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .tenantId(TenantService.MAIN_TENANT)
            .namespace(TEST_NAMESPACE)
            .tasks(List.of(Return.builder().id("task").type(Return.class.getName()).format(Property.ofValue("test")).build()))
            .build();
        flowService.create(GenericFlow.of(flow));
        reset(triggerEventQueue);

        // When — add a Schedule trigger
        Flow updated = flow.toBuilder()
            .triggers(List.of(Schedule.builder().id("schedule").type(Schedule.class.getName()).cron("0 0 * * *").build()))
            .build();
        flowService.update(GenericFlow.of(updated), GenericFlow.of(flow));

        // Then — a TriggerCreated event is emitted (not TriggerUpdated)
        var captor = org.mockito.ArgumentCaptor.forClass(TriggerEvent.class);
        verify(triggerEventQueue).send(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(TriggerCreated.class);
        assertThat(captor.getValue().id().getTriggerId()).isEqualTo("schedule");
    }

    @MockBean
    @Replaces(TriggerEventQueue.class)
    TriggerEventQueue triggerEventQueue() {
        return mock(TriggerEventQueue.class);
    }
}
