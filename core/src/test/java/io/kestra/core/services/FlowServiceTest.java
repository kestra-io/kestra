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
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.validations.ValidateConstraintViolation;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ConcurrencyLimitRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.FlowTopologyRepositoryInterface;
import io.kestra.core.runners.ConcurrencyLimit;
import io.kestra.core.scheduler.events.TriggerCreated;
import io.kestra.core.scheduler.events.TriggerEvent;
import io.kestra.core.scheduler.events.TriggerFlowRevisionUpdated;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @Inject
    private ConcurrencyLimitRepositoryInterface concurrencyLimitRepository;

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
    void shouldReturnTrueWhenValidatingFlow() {
        // Given
        String source = """
            id: test
            namespace: io.kestra.unittest
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: This is a message
            """;
        // When
        List<ValidateConstraintViolation> results = flowService.validate("my-tenant", List.of(new FlowSource(null, source)));

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.getFirst()).isEqualTo(new ValidateConstraintViolation(0, null, "io.kestra.unittest", "test", null, false, List.of(), List.of(), List.of()));
    }

    @Test
    void shouldReturnTrueWhenValidatingFlowWithFilename() {
        // Given
        String source = """
            id: test
            namespace: io.kestra.unittest
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: This is a message
            """;
        // When
        List<ValidateConstraintViolation> results = flowService.validate("my-tenant", List.of(new FlowSource("flow.yaml", source)));

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.getFirst()).isEqualTo(new ValidateConstraintViolation(0, "flow.yaml", "io.kestra.unittest", "test", null, false, List.of(), List.of(), List.of()));
    }

    @Test
    void shouldReturnConstraintWhenFlowUsesRemovedPluginDefaults() {
        // Given
        String source = """
            id: test
            namespace: io.kestra.unittest
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: This is a message
            pluginDefaults:
              - type: io.kestra.plugin.core.log.Log
                values:
                  level: WARN
            """;

        // When
        List<ValidateConstraintViolation> results = flowService.validate("my-tenant", List.of(new FlowSource(null, source)));

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getConstraints()).contains("pluginDefaults");
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
    void importFlow_ShouldEmitTriggerCreatedEventForNewTriggerInSyncedFlow() throws FlowProcessingException, QueueException {
        reset(triggerEventQueue);

    String source = """
        id: import_with_trigger
        namespace: some.namespace
        triggers:
          - id: daily
            type: io.kestra.plugin.core.trigger.Schedule
            cron: "0 6 * * *"
        tasks:
          - id: task
            type: io.kestra.plugin.core.log.Log
            message: Hello""";

    flowService.importFlow("my-tenant", source);
    verify(triggerEventQueue).send(any());
    
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
    void shouldResolveNestedFormInputPathWhenRenderingChecks() {
        // Given a check referencing a FORM child via its nested path, e.g. {{ inputs.environment.region }}
        Check check = Check.builder()
            .when("{{ inputs.environment.region == 'EU' }}")
            .message("region must be EU")
            .behavior(Check.Behavior.FAIL_EXECUTION)
            .build();
        Flow flow = mock(Flow.class);
        when(flow.getChecks()).thenReturn(List.of(check));
        when(flow.getNamespace()).thenReturn("io.kestra.unittest");
        when(flow.getId()).thenReturn("test");

        // When inputs are passed as the NESTED map (what ExecutionController/CreateExecutionForm hand in
        // for FORM inputs, via MapUtils.flattenToNestedMap), the nested path resolves...
        List<Check> nestedEu = flowService.getFailedChecks(flow, Map.of("environment", Map.of("region", "EU")));
        List<Check> nestedUs = flowService.getFailedChecks(flow, Map.of("environment", Map.of("region", "US")));

        // Then EU satisfies the condition (no failed check) and US fails it.
        assertThat(nestedEu).isEmpty();
        assertThat(nestedUs).containsExactly(check);

        // And a flat-dotted map (the un-nested form) cannot resolve the path — proving the nesting is required.
        List<Check> flatDotted = flowService.getFailedChecks(flow, Map.of("environment.region", "EU"));
        assertThat(flatDotted).isNotEmpty();
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
    void shouldInitConcurrencyLimitWhenCreatingFlowWithConcurrency() throws FlowProcessingException, QueueException {
        // Given a flow created with a concurrency limit
        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .tenantId(TenantService.MAIN_TENANT)
            .namespace(TEST_NAMESPACE)
            .tasks(List.of(Return.builder().id("task").type(Return.class.getName()).format(Property.ofValue("test")).build()))
            .concurrency(Concurrency.builder().limit(1).build())
            .build();

        // When
        flowService.create(GenericFlow.of(flow));

        // Then a concurrency limit is initialized at 0
        Optional<ConcurrencyLimit> limit = concurrencyLimitRepository.findById(flow.getTenantId(), flow.getNamespace(), flow.getId());
        assertThat(limit).isPresent();
        assertThat(limit.get().getRunning()).isEqualTo(0);
    }

    @Test
    void shouldNotInitConcurrencyLimitWhenCreatingFlowWithoutConcurrency() throws FlowProcessingException, QueueException {
        // Given a flow created without a concurrency limit
        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .tenantId(TenantService.MAIN_TENANT)
            .namespace(TEST_NAMESPACE)
            .tasks(List.of(Return.builder().id("task").type(Return.class.getName()).format(Property.ofValue("test")).build()))
            .build();

        // When
        flowService.create(GenericFlow.of(flow));

        // Then no concurrency limit is created
        Optional<ConcurrencyLimit> limit = concurrencyLimitRepository.findById(flow.getTenantId(), flow.getNamespace(), flow.getId());
        assertThat(limit).isEmpty();
    }

    @Test
    void shouldInitConcurrencyLimitWhenAddingConcurrencyOnUpdate() throws FlowProcessingException, QueueException {
        // Given a flow created without a concurrency limit
        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .tenantId(TenantService.MAIN_TENANT)
            .namespace(TEST_NAMESPACE)
            .tasks(List.of(Return.builder().id("task").type(Return.class.getName()).format(Property.ofValue("test")).build()))
            .build();
        FlowWithSource created = flowService.create(GenericFlow.of(flow));

        // When the flow is updated to add a concurrency limit
        Flow updated = flow.toBuilder()
            .concurrency(Concurrency.builder().limit(1).build())
            .build();
        flowService.update(GenericFlow.of(updated), created);

        // Then a concurrency limit is initialized at 0
        Optional<ConcurrencyLimit> limit = concurrencyLimitRepository.findById(flow.getTenantId(), flow.getNamespace(), flow.getId());
        assertThat(limit).isPresent();
        assertThat(limit.get().getRunning()).isEqualTo(0);
    }

    @Test
    void shouldRemoveConcurrencyLimitWhenRemovingConcurrencyOnUpdate() throws FlowProcessingException, QueueException {
        // Given a flow created with a concurrency limit
        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .tenantId(TenantService.MAIN_TENANT)
            .namespace(TEST_NAMESPACE)
            .tasks(List.of(Return.builder().id("task").type(Return.class.getName()).format(Property.ofValue("test")).build()))
            .concurrency(Concurrency.builder().limit(1).build())
            .build();
        FlowWithSource created = flowService.create(GenericFlow.of(flow));
        assertThat(concurrencyLimitRepository.findById(flow.getTenantId(), flow.getNamespace(), flow.getId())).isPresent();

        // When the flow is updated to remove the concurrency limit
        Flow updated = flow.toBuilder().concurrency(null).build();
        flowService.update(GenericFlow.of(updated), created);

        // Then the concurrency limit is removed
        Optional<ConcurrencyLimit> limit = concurrencyLimitRepository.findById(flow.getTenantId(), flow.getNamespace(), flow.getId());
        assertThat(limit).isEmpty();
    }

    @Test
    void shouldNotResetConcurrencyLimitWhenUpdatingFlowWithConcurrencyUnchanged() throws FlowProcessingException, QueueException {
        // Given a flow created with a concurrency limit and some running executions tracked
        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .tenantId(TenantService.MAIN_TENANT)
            .namespace(TEST_NAMESPACE)
            .tasks(List.of(Return.builder().id("task").type(Return.class.getName()).format(Property.ofValue("original")).build()))
            .concurrency(Concurrency.builder().limit(5).build())
            .build();
        FlowWithSource created = flowService.create(GenericFlow.of(flow));
        ConcurrencyLimit running = concurrencyLimitRepository.findById(flow.getTenantId(), flow.getNamespace(), flow.getId())
            .orElseThrow()
            .withRunning(3);
        concurrencyLimitRepository.update(running);

        // When the flow is updated but keeps a (non-null) concurrency limit
        Flow updated = flow.toBuilder()
            .tasks(List.of(Return.builder().id("task").type(Return.class.getName()).format(Property.ofValue("updated")).build()))
            .concurrency(Concurrency.builder().limit(10).build())
            .build();
        flowService.update(GenericFlow.of(updated), created);

        // Then the running counter is untouched (not reset to 0)
        Optional<ConcurrencyLimit> limit = concurrencyLimitRepository.findById(flow.getTenantId(), flow.getNamespace(), flow.getId());
        assertThat(limit).isPresent();
        assertThat(limit.get().getRunning()).isEqualTo(3);
    }

    @Test
    void shouldRemoveConcurrencyLimitWhenDeletingFlow() throws FlowProcessingException, QueueException {
        // Given a flow created with a concurrency limit
        Flow flow = Flow.builder()
            .id(IdUtils.create())
            .tenantId(TenantService.MAIN_TENANT)
            .namespace(TEST_NAMESPACE)
            .tasks(List.of(Return.builder().id("task").type(Return.class.getName()).format(Property.ofValue("test")).build()))
            .concurrency(Concurrency.builder().limit(1).build())
            .build();
        FlowWithSource created = flowService.create(GenericFlow.of(flow));
        assertThat(concurrencyLimitRepository.findById(flow.getTenantId(), flow.getNamespace(), flow.getId())).isPresent();

        // When the flow is deleted
        flowService.delete(created);

        // Then the concurrency limit is removed
        Optional<ConcurrencyLimit> limit = concurrencyLimitRepository.findById(flow.getTenantId(), flow.getNamespace(), flow.getId());
        assertThat(limit).isEmpty();
    }

    @Test
    void shouldInitConcurrencyLimitWhenRecreatingPreviouslyDeletedFlowWithConcurrency() throws FlowProcessingException, QueueException {
        // Given a flow created with a concurrency limit, then deleted (which removes the limit)
        String flowId = IdUtils.create();
        Flow flow = Flow.builder()
            .id(flowId)
            .tenantId(TenantService.MAIN_TENANT)
            .namespace(TEST_NAMESPACE)
            .tasks(List.of(Return.builder().id("task").type(Return.class.getName()).format(Property.ofValue("test")).build()))
            .concurrency(Concurrency.builder().limit(1).build())
            .build();
        FlowWithSource created = flowService.create(GenericFlow.of(flow));
        flowService.delete(created);
        assertThat(concurrencyLimitRepository.findById(flow.getTenantId(), flow.getNamespace(), flowId)).isEmpty();

        // When the flow is recreated with the same id and a concurrency limit
        Flow recreated = flow.toBuilder()
            .tasks(List.of(Return.builder().id("task").type(Return.class.getName()).format(Property.ofValue("revived")).build()))
            .build();
        flowService.create(GenericFlow.of(recreated));

        // Then the concurrency limit is re-initialized, since the previous (soft-deleted) revision
        // must not be treated as a live "previous" for the concurrency-limit sync.
        Optional<ConcurrencyLimit> limit = concurrencyLimitRepository.findById(flow.getTenantId(), flow.getNamespace(), flowId);
        assertThat(limit).isPresent();
        assertThat(limit.get().getRunning()).isEqualTo(0);
    }

    @Test
    void shouldNotInitConcurrencyLimitWhenCreatingDraftFlowWithConcurrency() throws FlowProcessingException, QueueException {
        // A draft flow is never picked up by the executor, so it must not get a live concurrency limit.
        String flowId = IdUtils.create();
        String source = """
            id: %s
            namespace: %s
            draft: true
            concurrency:
              limit: 1
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: hello
            """.formatted(flowId, TEST_NAMESPACE);

        FlowWithSource saved = flowService.create(GenericFlow.fromYaml(TenantService.MAIN_TENANT, source));

        try {
            assertThat(saved.isDraft()).isTrue();
            Optional<ConcurrencyLimit> limit = concurrencyLimitRepository.findById(saved.getTenantId(), saved.getNamespace(), flowId);
            assertThat(limit).isEmpty();
        } finally {
            flowRepository.findByIdWithSource(saved.getTenantId(), saved.getNamespace(), saved.getId())
                .ifPresent(f -> flowRepository.delete(f));
        }
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

    @Test
    void shouldNotEmitTriggerEventWhenCreatingDraftFlowWithScheduleTrigger() throws FlowProcessingException, QueueException {
        // A draft revision must be invisible to the scheduler: it is never picked up implicitly.
        // Creating a flow as a draft with a Schedule trigger must NOT emit any trigger lifecycle
        // event — otherwise the scheduler creates trigger state for the draft and fires the schedule.
        String flowId = IdUtils.create();
        String source = """
            id: %s
            namespace: %s
            draft: true
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: hello
            triggers:
              - id: schedule
                type: io.kestra.plugin.core.trigger.Schedule
                cron: "* * * * *"
            """.formatted(flowId, TEST_NAMESPACE);

        reset(triggerEventQueue);
        FlowWithSource saved = flowService.create(GenericFlow.fromYaml(TenantService.MAIN_TENANT, source));

        try {
            assertThat(saved.isDraft()).isTrue();
            verify(triggerEventQueue, never()).send(any());
        } finally {
            flowRepository.findByIdWithSource(saved.getTenantId(), saved.getNamespace(), saved.getId())
                .ifPresent(f -> flowRepository.delete(f));
        }
    }

    @Test
    void shouldNotEmitTriggerEventWhenUpdatingPublishedFlowToDraft() throws FlowProcessingException, QueueException {
        // Saving a draft on top of a published flow must leave the scheduler on the last non-draft
        // revision: no trigger event may be emitted, even when the draft changes the trigger.
        // Otherwise the scheduler would repoint its flow cache at the draft revision.
        String flowId = IdUtils.create();
        String publishedSource = """
            id: %s
            namespace: %s
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: hello
            triggers:
              - id: schedule
                type: io.kestra.plugin.core.trigger.Schedule
                cron: "0 0 * * *"
            """.formatted(flowId, TEST_NAMESPACE);
        FlowWithSource published = flowService.create(GenericFlow.fromYaml(TenantService.MAIN_TENANT, publishedSource));

        try {
            reset(triggerEventQueue);

            String draftSource = """
                id: %s
                namespace: %s
                draft: true
                tasks:
                  - id: log
                    type: io.kestra.plugin.core.log.Log
                    message: hello
                triggers:
                  - id: schedule
                    type: io.kestra.plugin.core.trigger.Schedule
                    cron: "*/5 * * * *"
                """.formatted(flowId, TEST_NAMESPACE);
            FlowWithSource draft = flowService.update(GenericFlow.fromYaml(TenantService.MAIN_TENANT, draftSource), published);

            assertThat(draft.isDraft()).isTrue();
            assertThat(draft.getRevision()).isEqualTo(2);
            verify(triggerEventQueue, never()).send(any());
        } finally {
            flowRepository.findByIdWithSource(published.getTenantId(), published.getNamespace(), published.getId())
                .ifPresent(f -> flowRepository.delete(f));
        }
    }

    @MockBean
    @Replaces(TriggerEventQueue.class)
    TriggerEventQueue triggerEventQueue() {
        return mock(TriggerEventQueue.class);
    }

    @Test
    void shouldAllowSavingDraftWithMissingTasks() throws FlowProcessingException, QueueException {
        // A draft is allowed to be saved invalid (here: empty tasks list violates @NotEmpty).
        // It will fail at execution time, but persisting it lets the user keep iterating.
        String flowId = IdUtils.create();
        String source = """
            id: %s
            namespace: %s
            draft: true
            tasks: []
            """.formatted(flowId, TEST_NAMESPACE);

        FlowWithSource saved = flowService.create(GenericFlow.fromYaml(TenantService.MAIN_TENANT, source));

        try {
            assertThat(saved).isNotNull();
            assertThat(saved.isDraft()).isTrue();
        } finally {
            flowRepository.findByIdWithSource(saved.getTenantId(), saved.getNamespace(), saved.getId())
                .ifPresent(f -> flowRepository.delete(f));
        }
    }

    @Test
    void shouldRejectSavingNonDraftWithMissingTasks() {
        // Belt-and-braces: the same invalid flow without draft:true must still be refused so we
        // do not regress the validation guarantee on published flows.
        String flowId = IdUtils.create();
        String source = """
            id: %s
            namespace: %s
            tasks: []
            """.formatted(flowId, TEST_NAMESPACE);

        assertThatThrownBy(
            () -> flowService.create(GenericFlow.fromYaml(TenantService.MAIN_TENANT, source))
        ).isInstanceOf(jakarta.validation.ConstraintViolationException.class);
    }

    @Test
    void shouldAllowSavingDraftWithUnrecognizedTaskProperty() throws FlowProcessingException, QueueException {
        // Regression: creating a draft whose task has an unrecognized property used to return 422
        // because FlowService applied strict parsing regardless of the draft flag.
        String flowId = IdUtils.create();
        String source = """
            id: %s
            namespace: %s
            draft: true
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: hello
                unknownProp: someValue
            """.formatted(flowId, TEST_NAMESPACE);

        FlowWithSource saved = flowService.create(GenericFlow.fromYaml(TenantService.MAIN_TENANT, source));

        try {
            assertThat(saved).isNotNull();
            assertThat(saved.isDraft()).isTrue();
        } finally {
            flowRepository.findByIdWithSource(saved.getTenantId(), saved.getNamespace(), saved.getId())
                .ifPresent(f -> flowRepository.delete(f));
        }
    }

    @Test
    void shouldAllowUpdatingDraftWithUnrecognizedTaskProperty() throws FlowProcessingException, QueueException {
        // Same as above but for the update path.
        String flowId = IdUtils.create();
        String validSource = """
            id: %s
            namespace: %s
            tasks:
              - id: log
                type: io.kestra.plugin.core.log.Log
                message: hello
            """.formatted(flowId, TEST_NAMESPACE);

        FlowWithSource created = flowService.create(GenericFlow.fromYaml(TenantService.MAIN_TENANT, validSource));

        try {
            String draftSource = """
                id: %s
                namespace: %s
                draft: true
                tasks:
                  - id: log
                    type: io.kestra.plugin.core.log.Log
                    message: hello
                    unknownProp: someValue
                """.formatted(flowId, TEST_NAMESPACE);

            FlowWithSource updated = flowService.update(
                GenericFlow.fromYaml(TenantService.MAIN_TENANT, draftSource),
                created
            );

            assertThat(updated).isNotNull();
            assertThat(updated.isDraft()).isTrue();
        } finally {
            flowRepository.findByIdWithSource(created.getTenantId(), created.getNamespace(), created.getId())
                .ifPresent(f -> flowRepository.delete(f));
        }
    }

    @Test
    void shouldReportConstraintViolationsWhenValidatingInvalidDraftForExecution() throws FlowProcessingException, QueueException {
        // After an invalid draft is saved, validateForExecution must surface the violations so
        // the caller (ExecutionController) can mark the execution as FAILED rather than running
        // an under-defined flow that would blow up later in a confusing way.
        String flowId = IdUtils.create();
        String source = """
            id: %s
            namespace: %s
            draft: true
            tasks: []
            """.formatted(flowId, TEST_NAMESPACE);

        FlowWithSource saved = flowService.create(GenericFlow.fromYaml(TenantService.MAIN_TENANT, source));

        try {
            Optional<jakarta.validation.ConstraintViolationException> violations = flowService.validateForExecution(saved.toFlow());
            assertThat(violations).isPresent();
            // The flow has at least one violation - we don't pin the exact message so this stays
            // robust against bean-validation message wording changes.
            assertThat(violations.get().getConstraintViolations()).isNotEmpty();
        } finally {
            flowRepository.findByIdWithSource(saved.getTenantId(), saved.getNamespace(), saved.getId())
                .ifPresent(f -> flowRepository.delete(f));
        }
    }
}
