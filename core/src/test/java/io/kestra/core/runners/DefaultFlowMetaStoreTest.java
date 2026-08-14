package io.kestra.core.runners;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.FlowBlockedException;
import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.FlowService;
import io.kestra.core.services.FlowParsingService;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.debug.Return;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@KestraTest
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
class DefaultFlowMetaStoreTest {
    @Inject
    private DefaultFlowMetaStore flowMetaStore;

    @Inject
    private FlowWithDefaultCache flowWithDefaultCache;

    @Inject
    private FlowService flowService;

    @AfterEach
    void clean() {
        flowMetaStore.clearCache();
        flowWithDefaultCache.flushAll();
    }

    @Test
    void findById() throws FlowProcessingException, QueueException {
        FlowWithSource test = flowService.create(GenericFlow.of(createFlow()));

        Optional<FlowInterface> maybeFlow = flowMetaStore.findById(test.getTenantId(), test.getNamespace(), test.getId(), Optional.empty());

        assertThat(maybeFlow).isPresent();
        assertThat(maybeFlow.get().getId()).isEqualTo(test.getId());

        flowService.delete(test);
    }

    @Test
    void findByIdShouldReturnEmptyForAbsentFlow() {
        Flow test = createFlow();
        Optional<FlowInterface> maybeFlow = flowMetaStore.findById(test.getTenantId(), test.getNamespace(), test.getId(), Optional.empty());

        assertThat(maybeFlow).isEmpty();
    }

    @Test
    void findByIdShouldReturnLastRevision() throws FlowProcessingException, QueueException {
        FlowWithSource test = flowService.create(GenericFlow.of(createFlow()));
        Flow toUpdate = test.toBuilder()
            .tasks(List.of(Return.builder().id("return").format(Property.ofValue("new format")).type(Return.class.getName()).build()))
            .build()
            .toFlow(); // otherwise the source didn't change so no new revisions will be created
        FlowWithSource updated = flowService.update(GenericFlow.of(toUpdate), test);

        Optional<FlowInterface> maybeFlow = flowMetaStore.findById(test.getTenantId(), test.getNamespace(), test.getId(), Optional.of(2));

        assertThat(maybeFlow).isPresent();
        assertThat(maybeFlow.get().getId()).isEqualTo(test.getId());
        assertThat(maybeFlow.get().getRevision()).isEqualTo(2);

        flowService.delete(updated);
    }

    @Test
    void findByIdShouldReturnPreviousRevision() throws FlowProcessingException, QueueException {
        FlowWithSource test = flowService.create(GenericFlow.of(createFlow()));
        flowService.update(GenericFlow.of(test.toBuilder().revision(2).build()), test);

        Optional<FlowInterface> maybeFlow = flowMetaStore.findById(test.getTenantId(), test.getNamespace(), test.getId(), Optional.of(1));

        assertThat(maybeFlow).isPresent();
        assertThat(maybeFlow.get().getId()).isEqualTo(test.getId());
        assertThat(maybeFlow.get().getRevision()).isEqualTo(1);

        flowService.delete(test);
    }

    @Test
    void findByIdShouldReturnEmptyForDeletedFlow() throws FlowProcessingException, QueueException {
        FlowWithSource test = flowService.create(GenericFlow.of(createFlow()));
        flowService.delete(test);

        // the metastore cache is fed asynchronously by a polling flow-queue subscriber
        await()
            .atMost(Duration.ofSeconds(10))
            .until(() -> flowMetaStore.findById(test.getTenantId(), test.getNamespace(), test.getId(), Optional.empty()).isEmpty());

        Optional<FlowInterface> maybeFlow = flowMetaStore.findById(test.getTenantId(), test.getNamespace(), test.getId(), Optional.empty());

        assertThat(maybeFlow).isEmpty();
    }

    @Test
    void findByExecution() throws FlowProcessingException, QueueException {
        Flow test = createFlow();
        FlowWithSource created = flowService.create(GenericFlow.of(test));
        Execution execution = Execution.newExecution(created, null, null, Optional.empty());

        Optional<FlowInterface> maybeFlow = flowMetaStore.findByExecution(execution);

        assertThat(maybeFlow).isPresent();
        assertThat(maybeFlow.get().getId()).isEqualTo(test.getId());

        flowService.delete(created);
    }

    @Test
    void findByExecutionShouldReturnEmptyForAbsentFlow() {
        Flow test = createFlow();
        Execution execution = Execution.newExecution(test, null, null, Optional.empty());

        Optional<FlowInterface> maybeFlow = flowMetaStore.findByExecution(execution);

        assertThat(maybeFlow).isEmpty();
    }

    @Test
    void findByExecutionForRuntime() throws FlowProcessingException, QueueException {
        Flow test = createFlow();
        FlowWithSource created = flowService.create(GenericFlow.of(test));
        Execution execution = Execution.newExecution(created, null, null, Optional.empty());

        Optional<FlowWithSource> maybeFlow = flowMetaStore.findByExecutionForRuntime(execution);

        assertThat(maybeFlow).isPresent();
        assertThat(maybeFlow.get().getId()).isEqualTo(test.getId());

        flowService.delete(created);
    }

    @Test
    void findByExecutionForRuntimeShouldReturnEmptyForAbsentFlow() {
        Flow test = createFlow();
        Execution execution = Execution.newExecution(test, null, null, Optional.empty());

        Optional<FlowWithSource> maybeFlow = flowMetaStore.findByExecutionForRuntime(execution);

        assertThat(maybeFlow).isEmpty();
    }

    @Test
    void shouldReturnFlowProcessedForRuntimeWhenFindingById() throws FlowProcessingException {
        // Given a stored flow and a parsing service processing it for runtime
        FlowWithSource flow = createFlow().toBuilder().revision(1).build();
        FlowWithSource processed = flow.toBuilder().labels(List.of(new Label("team", "platform"))).build();
        FlowParsingService parsingService = mock(FlowParsingService.class);
        when(parsingService.parseForRuntime(flow)).thenReturn(processed);
        DefaultFlowMetaStore metaStore = metaStore(flow, parsingService);

        // When
        Optional<FlowWithSource> resolved = metaStore.findByIdForRuntime(flow.getTenantId(), flow.getNamespace(), flow.getId(), Optional.of(1));

        // Then the flow is the one processed for runtime, not the one as stored
        assertThat(resolved).isPresent();
        assertThat(resolved.get().getLabels()).containsExactly(new Label("team", "platform"));
        verify(parsingService).parseForRuntime(flow);
    }

    @Test
    void shouldReturnFlowProcessedForRuntimeWhenFindingByIdFromTask() throws FlowProcessingException {
        // Given a stored flow and a parsing service processing it for runtime
        FlowWithSource flow = createFlow().toBuilder().revision(1).build();
        FlowWithSource processed = flow.toBuilder().labels(List.of(new Label("team", "platform"))).build();
        FlowParsingService parsingService = mock(FlowParsingService.class);
        when(parsingService.parseForRuntime(flow)).thenReturn(processed);
        DefaultFlowMetaStore metaStore = metaStore(flow, parsingService);

        // When a subflow task resolves it
        Optional<FlowWithSource> resolved = metaStore.findByIdFromTaskForRuntime(
            flow.getTenantId(), flow.getNamespace(), flow.getId(), Optional.of(1),
            flow.getTenantId(), flow.getNamespace(), "parent"
        );

        // Then the child execution is built from the flow processed for runtime
        assertThat(resolved).isPresent();
        assertThat(resolved.get().getLabels()).containsExactly(new Label("team", "platform"));
        verify(parsingService).parseForRuntime(flow);
    }

    @Test
    void shouldSurfaceBlockedFlowAsFlowWithExceptionOnCreationPath() throws FlowProcessingException {
        // Given a parsing service rejecting the flow at runtime
        FlowWithSource flow = createFlow().toBuilder().revision(1).build();
        FlowParsingService parsingService = mock(FlowParsingService.class);
        when(parsingService.parseForRuntime(flow)).thenThrow(new FlowBlockedException("Blocked by governance policy"));
        DefaultFlowMetaStore metaStore = metaStore(flow, parsingService);

        // When
        Optional<FlowWithSource> resolved = metaStore.findByIdForRuntime(flow.getTenantId(), flow.getNamespace(), flow.getId(), Optional.of(1));

        // Then the rejection is surfaced as a FlowWithException the executor fails fast on — never a throw
        assertThat(resolved).isPresent();
        assertThat(resolved.get()).isInstanceOf(FlowWithException.class);
        assertThat(((FlowWithException) resolved.get()).getException()).contains("Blocked by governance policy");
    }

    @Test
    void shouldDegradeToStoredFlowWhenRuntimeParsingFailsOnCreationPath() throws FlowProcessingException {
        // Given a parsing service failing on a non-governance error
        FlowWithSource flow = createFlow().toBuilder().revision(1).build();
        FlowParsingService parsingService = mock(FlowParsingService.class);
        when(parsingService.parseForRuntime(flow)).thenThrow(new FlowProcessingException("invalid"));
        DefaultFlowMetaStore metaStore = metaStore(flow, parsingService);

        // When
        Optional<FlowWithSource> resolved = metaStore.findByIdForRuntime(flow.getTenantId(), flow.getNamespace(), flow.getId(), Optional.of(1));

        // Then the execution proceeds with the flow as stored — never a throw
        assertThat(resolved).isPresent();
        assertThat(resolved.get()).isNotInstanceOf(FlowWithException.class);
        assertThat(resolved.get().getId()).isEqualTo(flow.getId());
        assertThat(resolved.get().getRevision()).isEqualTo(flow.getRevision());
        assertThat(resolved.get().getLabels()).isNullOrEmpty();
    }

    @Test
    void allLastVersion() throws FlowProcessingException, QueueException {
        FlowWithSource test1 = flowService.create(GenericFlow.of(createFlow()));
        FlowWithSource test2 = flowService.create(GenericFlow.of(createFlow()));

        // the metastore cache is fed asynchronously by a polling flow-queue subscriber
        await()
            .atMost(Duration.ofSeconds(10))
            .untilAsserted(() -> assertThat(flowMetaStore.allLastVersion())
                .extracting(flow -> flow.getId())
                .contains(test1.getId(), test2.getId()));

        flowService.delete(test1);
        flowService.delete(test2);
    }

    @Test
    void findByIdFromTask() throws FlowProcessingException, QueueException {
        FlowWithSource test = flowService.create(GenericFlow.of(createFlow()));

        Optional<FlowInterface> maybeFlow = flowMetaStore
            .findByIdFromTask(test.getTenantId(), test.getNamespace(), test.getId(), Optional.empty(), test.getTenantId(), test.getNamespace(), test.getId());

        assertThat(maybeFlow).isPresent();
        assertThat(maybeFlow.get().getId()).isEqualTo(test.getId());

        flowService.delete(test);
    }

    @Test
    void findByIdFromTaskShouldReturnEmptyForAbsentFlow() {
        Flow test = createFlow();
        Optional<FlowInterface> maybeFlow = flowMetaStore
            .findByIdFromTask(test.getTenantId(), test.getNamespace(), test.getId(), Optional.empty(), test.getTenantId(), test.getNamespace(), test.getId());

        assertThat(maybeFlow).isEmpty();
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void findByIdFromTaskShouldResolveLatestNonDraftWhenCachedHeadIsADraft() {
        // A subflow with no explicit revision resolves the child flow via findByIdFromTask -> findById
        // with an empty revision. If the latest (cached) revision is a draft it must be dropped and the
        // latest NON-draft revision returned from the execution-time lookup, so a subflow never runs a
        // draft child (mirroring webhooks/schedules/Flow triggers). Deterministic unit: the metastore
        // cache is seeded from findAllWithSourceForAllTenants() in the constructor.
        String tenant = TenantService.MAIN_TENANT;
        String namespace = "io.kestra.tests";
        String id = IdUtils.create();

        FlowWithSource draftHead = FlowWithSource.builder()
            .tenantId(tenant).namespace(namespace).id(id).revision(2).draft(true)
            .tasks(List.of(Return.builder().id("return").format(Property.ofValue("draft")).type(Return.class.getName()).build()))
            .build();
        FlowWithSource publishedRevision = FlowWithSource.builder()
            .tenantId(tenant).namespace(namespace).id(id).revision(1).draft(false)
            .tasks(List.of(Return.builder().id("return").format(Property.ofValue("published")).type(Return.class.getName()).build()))
            .build();

        FlowRepositoryInterface repository = mock(FlowRepositoryInterface.class);
        // the metastore caches the latest revision (the draft head) at construction...
        when(repository.findAllWithSourceForAllTenants()).thenReturn(List.of(draftHead));
        // ...and the execution-time (draft-filtering) lookup returns the latest non-draft revision.
        when(repository.findByIdWithSourceForExecution(tenant, namespace, id)).thenReturn(Optional.of(publishedRevision));

        DefaultFlowMetaStore metaStore = new DefaultFlowMetaStore(
            repository,
            mock(FlowParsingService.class),
            mock(RunContextLoggerFactory.class),
            mock(BroadcastQueueInterface.class),
            mock(FlowWithDefaultCache.class)
        );

        Optional<FlowInterface> resolved = metaStore.findByIdFromTask(
            tenant, namespace, id, Optional.empty(),
            tenant, namespace, id
        );

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getRevision()).isEqualTo(1);
        assertThat(resolved.get().isDraft()).isFalse();
        // the draft head must not be served from cache; the non-draft fallback must be used
        verify(repository).findByIdWithSourceForExecution(tenant, namespace, id);
    }

    @Test
    void shouldSurfaceBlockedFlowAsFlowWithExceptionOnExecutionPath() throws FlowProcessingException {
        // Given a parsing service rejecting the flow at runtime
        FlowWithSource flow = createFlow().toBuilder().revision(1).build();
        FlowParsingService parsingService = mock(FlowParsingService.class);
        when(parsingService.parseForRuntime(flow)).thenThrow(new FlowBlockedException("Blocked by governance policy"));
        DefaultFlowMetaStore metaStore = metaStore(flow, parsingService);

        // When
        Optional<FlowWithSource> resolved = metaStore.findByExecutionForRuntime(executionOf(flow));

        // Then the rejection is surfaced as a FlowWithException the executor fails fast on — never a throw
        assertThat(resolved).isPresent();
        assertThat(resolved.get()).isInstanceOf(FlowWithException.class);
        assertThat(((FlowWithException) resolved.get()).getException()).contains("Blocked by governance policy");
    }

    @Test
    void shouldDegradeToStoredFlowWhenRuntimeParsingFailsOnExecutionPath() throws FlowProcessingException {
        // Given a parsing service failing on a non-governance error
        FlowWithSource flow = createFlow().toBuilder().revision(1).build();
        FlowParsingService parsingService = mock(FlowParsingService.class);
        when(parsingService.parseForRuntime(flow)).thenThrow(new FlowProcessingException("invalid"));
        DefaultFlowMetaStore metaStore = metaStore(flow, parsingService);

        // When
        Optional<FlowWithSource> resolved = metaStore.findByExecutionForRuntime(executionOf(flow));

        // Then the execution proceeds with the flow as stored — never a throw
        assertThat(resolved).isPresent();
        assertThat(resolved.get()).isNotInstanceOf(FlowWithException.class);
        assertThat(resolved.get().getId()).isEqualTo(flow.getId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldServeCachedFlowWithoutResolvingItWhenFindingByExecution() throws FlowProcessingException {
        // Given an execution pinned to a revision the meta-store no longer holds, already memoized for runtime
        FlowWithSource head = createFlow().toBuilder().revision(2).build();
        FlowWithSource processed = head.toBuilder().revision(1).labels(List.of(new Label("team", "platform"))).build();
        Execution execution = Execution.builder()
            .id(IdUtils.create())
            .tenantId(head.getTenantId())
            .namespace(head.getNamespace())
            .flowId(head.getId())
            .flowRevision(1)
            .build();

        FlowRepositoryInterface repository = mock(FlowRepositoryInterface.class);
        when(repository.findAllWithSourceForAllTenants()).thenReturn(List.of(head));
        FlowParsingService parsingService = mock(FlowParsingService.class);
        FlowWithDefaultCache withDefaultCache = mock(FlowWithDefaultCache.class);
        when(withDefaultCache.getIfPresent(FlowId.uid(head.getTenantId(), head.getNamespace(), head.getId(), Optional.of(1))))
            .thenReturn(Optional.of(processed));

        DefaultFlowMetaStore metaStore = new DefaultFlowMetaStore(
            repository, parsingService, mock(RunContextLoggerFactory.class), mock(BroadcastQueueInterface.class), withDefaultCache
        );

        // When
        Optional<FlowWithSource> resolved = metaStore.findByExecutionForRuntime(execution);

        // Then the memoized flow is served without a repository lookup — this runs on every executor message
        assertThat(resolved).isPresent();
        assertThat(resolved.get().getLabels()).containsExactly(new Label("team", "platform"));
        verify(repository, never()).findByIdWithSource(any(), any(), any(), any());
        verify(parsingService, never()).parseForRuntime(any());
    }

    @SuppressWarnings("unchecked")
    private DefaultFlowMetaStore metaStore(FlowWithSource cachedFlow, FlowParsingService parsingService) {
        FlowRepositoryInterface repository = mock(FlowRepositoryInterface.class);
        when(repository.findAllWithSourceForAllTenants()).thenReturn(List.of(cachedFlow));

        RunContextLoggerFactory loggerFactory = mock(RunContextLoggerFactory.class);
        when(loggerFactory.create(org.mockito.ArgumentMatchers.any(Execution.class))).thenReturn(mock(RunContextLogger.class));

        FlowWithDefaultCache withDefaultCache = mock(FlowWithDefaultCache.class);
        when(withDefaultCache.getIfPresent(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());

        return new DefaultFlowMetaStore(repository, parsingService, loggerFactory, mock(BroadcastQueueInterface.class), withDefaultCache);
    }

    private static Execution executionOf(FlowWithSource flow) {
        return Execution.builder()
            .id(IdUtils.create())
            .tenantId(flow.getTenantId())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(flow.getRevision())
            .build();
    }

    private FlowWithSource createFlow() {
        return FlowWithSource.builder()
            .tenantId(TenantService.MAIN_TENANT)
            .namespace("io.kestra.tests")
            .id(IdUtils.create())
            .tasks(List.of(Return.builder().id("return").format(Property.ofValue("format")).type(Return.class.getName()).build()))
            .build();
    }
}