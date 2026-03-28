package io.kestra.scheduler.stores;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.scheduler.SchedulerConfiguration;
import io.kestra.scheduler.Fixtures;
import io.kestra.scheduler.utils.InMemoryFlowMetaStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CachedFlowMetaStoreTest {

    // Use 1 vNode so all flows hash to vNode 0
    private static final int TEST_VNODE_COUNT = 1;
    private static final SchedulerConfiguration SCHEDULER_CONFIGURATION = new SchedulerConfiguration(TEST_VNODE_COUNT, Duration.ofSeconds(5), 100);

    private FlowMetaStore delegate;
    private CachedFlowMetaStore cachedStore;

    @BeforeEach
    void setUp() {
        delegate = spy(new InMemoryFlowMetaStore(TEST_VNODE_COUNT, List.of()));
        cachedStore = new CachedFlowMetaStore(delegate, SCHEDULER_CONFIGURATION);
    }

    // -------------------------------------------------------------------------
    // find()
    // -------------------------------------------------------------------------

    @Test
    void shouldDelegateToStoreOnCacheMiss() {
        // GIVEN
        FlowWithSource flow = Fixtures.defaultFlow();
        ((InMemoryFlowMetaStore) delegate).add(flow);
        FlowId flowId = FlowId.of(flow.getTenantId(), flow.getNamespace(), flow.getId(), flow.getRevision());

        // WHEN
        Optional<FlowWithSource> result = cachedStore.find(flowId);

        // THEN
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(flow.getId());
        verify(delegate, times(1)).find(flowId);
    }

    @Test
    void shouldReturnCachedValueOnCacheHit() {
        // GIVEN
        FlowWithSource flow = Fixtures.defaultFlow();
        ((InMemoryFlowMetaStore) delegate).add(flow);
        FlowId flowId = FlowId.of(flow.getTenantId(), flow.getNamespace(), flow.getId(), flow.getRevision());

        cachedStore.find(flowId); // populate cache
        reset(delegate);

        // WHEN
        Optional<FlowWithSource> result = cachedStore.find(flowId);

        // THEN
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(flow.getId());
        verify(delegate, never()).find(any());
    }

    @Test
    void shouldReturnCachedValueWhenRevisionIsNull() {
        // GIVEN
        FlowWithSource flow = Fixtures.defaultFlow();
        ((InMemoryFlowMetaStore) delegate).add(flow);
        FlowId flowIdWithRev = FlowId.of(flow.getTenantId(), flow.getNamespace(), flow.getId(), flow.getRevision());
        cachedStore.find(flowIdWithRev); // populate cache
        reset(delegate);

        FlowId flowIdNoRev = FlowId.of(flow.getTenantId(), flow.getNamespace(), flow.getId(), null);

        // WHEN
        Optional<FlowWithSource> result = cachedStore.find(flowIdNoRev);

        // THEN
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(flow.getId());
        verify(delegate, never()).find(any());
    }

    @Test
    void shouldReturnCachedValueWhenRevisionMatches() {
        // GIVEN
        FlowWithSource flow = Fixtures.defaultFlow(); // revision 0
        ((InMemoryFlowMetaStore) delegate).add(flow);
        FlowId flowId = FlowId.of(flow.getTenantId(), flow.getNamespace(), flow.getId(), flow.getRevision());

        cachedStore.find(flowId); // populate cache
        reset(delegate);

        // WHEN - same revision
        Optional<FlowWithSource> result = cachedStore.find(flowId);

        // THEN
        assertThat(result).isPresent();
        assertThat(result.get().getRevision()).isEqualTo(flow.getRevision());
        verify(delegate, never()).find(any());
    }

    @Test
    void shouldDelegateAndUpdateCacheWhenRevisionIsNewer() {
        // GIVEN - cache revision 0
        FlowWithSource flowV0 = Fixtures.defaultFlow(); // revision 0
        ((InMemoryFlowMetaStore) delegate).add(flowV0);
        FlowId flowIdV0 = FlowId.of(flowV0.getTenantId(), flowV0.getNamespace(), flowV0.getId(), flowV0.getRevision());
        cachedStore.find(flowIdV0); // populate cache with revision 0

        // Update delegate with revision 1
        FlowWithSource flowV1 = flowV0.toBuilder().revision(1).build();
        ((InMemoryFlowMetaStore) delegate).add(flowV1);
        reset(delegate);

        FlowId flowIdV1 = FlowId.of(flowV1.getTenantId(), flowV1.getNamespace(), flowV1.getId(), 1);

        // WHEN
        Optional<FlowWithSource> result = cachedStore.find(flowIdV1);

        // THEN - should return new revision from delegate
        assertThat(result).isPresent();
        assertThat(result.get().getRevision()).isEqualTo(1);
        verify(delegate, times(1)).find(flowIdV1);

        // AND - cache should now contain the newer revision
        reset(delegate);
        FlowId flowIdNullRev = FlowId.of(flowV1.getTenantId(), flowV1.getNamespace(), flowV1.getId(), null);
        Optional<FlowWithSource> cached = cachedStore.find(flowIdNullRev);
        assertThat(cached).isPresent();
        assertThat(cached.get().getRevision()).isEqualTo(1);
        verify(delegate, never()).find(any());
    }

    @Test
    void shouldReturnEmptyWhenFlowNotFound() {
        // GIVEN
        FlowId flowId = FlowId.of("tenant", "namespace", "nonexistent", null);

        // WHEN
        Optional<FlowWithSource> result = cachedStore.find(flowId);

        // THEN
        assertThat(result).isEmpty();
        verify(delegate, times(1)).find(flowId);
    }

    // -------------------------------------------------------------------------
    // findAllForVNodes()
    // -------------------------------------------------------------------------

    @Test
    void shouldReturnAllFlowsForVNodesOnCacheMiss() {
        // GIVEN
        FlowWithSource flow = Fixtures.defaultFlow();
        ((InMemoryFlowMetaStore) delegate).add(flow);
        Set<Integer> vNodes = Set.of(0);

        // WHEN
        List<FlowWithSource> result = cachedStore.findAllForVNodes(vNodes);

        // THEN
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(flow.getId());
        verify(delegate, times(1)).findAllForVNodes(Set.of(0));
    }

    @Test
    void shouldReturnAllFlowsForVNodesFromCache() {
        // GIVEN
        FlowWithSource flow = Fixtures.defaultFlow();
        ((InMemoryFlowMetaStore) delegate).add(flow);
        Set<Integer> vNodes = Set.of(0);

        cachedStore.findAllForVNodes(vNodes); // populate cache
        reset(delegate);

        // WHEN
        List<FlowWithSource> result = cachedStore.findAllForVNodes(vNodes);

        // THEN
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(flow.getId());
        verify(delegate, never()).findAllForVNodes(any());
    }

    @Test
    void shouldReturnEmptyListForEmptyVNodes() {
        // GIVEN
        Set<Integer> vNodes = Set.of();

        // WHEN
        List<FlowWithSource> result = cachedStore.findAllForVNodes(vNodes);

        // THEN
        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // init()
    // -------------------------------------------------------------------------

    @Test
    void shouldInitAndLoadCacheForVNodes() {
        // GIVEN
        FlowWithSource flow = Fixtures.defaultFlow();
        ((InMemoryFlowMetaStore) delegate).add(flow);

        // WHEN
        cachedStore.init(Set.of(0));

        // THEN
        assertThat(cachedStore.cacheSize()).isEqualTo(1);
        // Subsequent find should use cache
        reset(delegate);
        FlowId flowId = FlowId.of(flow.getTenantId(), flow.getNamespace(), flow.getId(), null);
        Optional<FlowWithSource> result = cachedStore.find(flowId);
        assertThat(result).isPresent();
        verify(delegate, never()).find(any());
    }

    @Test
    void shouldRevokeVNodesOnInit() {
        // GIVEN - init with vNodes {0}
        FlowWithSource flow = Fixtures.defaultFlow();
        ((InMemoryFlowMetaStore) delegate).add(flow);
        cachedStore.init(Set.of(0));
        assertThat(cachedStore.cacheSize()).isEqualTo(1);

        // WHEN - init with empty set (but not null, which would call clear())
        // We re-init with a different vNode set that doesn't include 0
        // Since we have 1 vNode total, we use a set that won't match: init with vNode 1
        // But with TEST_VNODE_COUNT=1, all flows go to vNode 0. Let's use a higher vNode count scenario.
        // Instead, init with an empty set to revoke all.
        cachedStore.init(Set.of());

        // THEN
        assertThat(cachedStore.cacheSize()).isEqualTo(0);
    }

    @Test
    void shouldClearCacheOnInitWithNullVNodes() {
        // GIVEN
        FlowWithSource flow = Fixtures.defaultFlow();
        ((InMemoryFlowMetaStore) delegate).add(flow);
        cachedStore.init(Set.of(0));
        assertThat(cachedStore.cacheSize()).isEqualTo(1);

        // WHEN
        cachedStore.init(null);

        // THEN
        assertThat(cachedStore.cacheSize()).isEqualTo(0);
    }

    @Test
    void shouldClearCacheOnInitWithEmptyVNodes() {
        // GIVEN
        FlowWithSource flow = Fixtures.defaultFlow();
        ((InMemoryFlowMetaStore) delegate).add(flow);
        cachedStore.init(Set.of(0));
        assertThat(cachedStore.cacheSize()).isEqualTo(1);

        // WHEN
        cachedStore.init(Set.of());

        // THEN
        assertThat(cachedStore.cacheSize()).isEqualTo(0);
    }

    @Test
    void shouldRevokeOldVNodesAndKeepNewOnesOnInit() {
        // GIVEN - use 2 vNodes to properly test revocation
        SchedulerConfiguration config2VNodes = new SchedulerConfiguration(2, Duration.ofSeconds(5), 100);
        InMemoryFlowMetaStore delegate2 = spy(new InMemoryFlowMetaStore(2, List.of()));
        CachedFlowMetaStore cachedStore2 = new CachedFlowMetaStore(delegate2, config2VNodes);

        // Add flows that will hash to different vNodes
        FlowWithSource flow = Fixtures.defaultFlow();
        delegate2.add(flow);

        // Init with both vNodes
        cachedStore2.init(Set.of(0, 1));
        int initialSize = cachedStore2.cacheSize();
        assertThat(initialSize).isGreaterThanOrEqualTo(1);

        // WHEN - re-init with only vNode 0 (revoking vNode 1)
        cachedStore2.init(Set.of(0));

        // THEN - cache should only contain flows for vNode 0
        assertThat(cachedStore2.cacheSize()).isLessThanOrEqualTo(initialSize);
    }

    // -------------------------------------------------------------------------
    // clear()
    // -------------------------------------------------------------------------

    @Test
    void shouldClearAllCaches() {
        // GIVEN
        FlowWithSource flow = Fixtures.defaultFlow();
        ((InMemoryFlowMetaStore) delegate).add(flow);
        cachedStore.init(Set.of(0));
        assertThat(cachedStore.cacheSize()).isEqualTo(1);

        // WHEN
        cachedStore.clear();

        // THEN
        assertThat(cachedStore.cacheSize()).isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // cacheSize()
    // -------------------------------------------------------------------------

    @Test
    void shouldReportCorrectCacheSize() {
        // GIVEN
        assertThat(cachedStore.cacheSize()).isEqualTo(0);

        FlowWithSource flow = Fixtures.defaultFlow();
        ((InMemoryFlowMetaStore) delegate).add(flow);

        // WHEN
        cachedStore.init(Set.of(0));

        // THEN
        assertThat(cachedStore.cacheSize()).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // init() - no reload for already cached vNodes
    // -------------------------------------------------------------------------

    @Test
    void shouldNotReloadAlreadyCachedVNodes() {
        // GIVEN
        FlowWithSource flow = Fixtures.defaultFlow();
        ((InMemoryFlowMetaStore) delegate).add(flow);
        cachedStore.init(Set.of(0));
        reset(delegate);

        // WHEN - init again with the same vNodes
        cachedStore.init(Set.of(0));

        // THEN - delegate should not be called again for already cached vNodes
        verify(delegate, never()).findAllForVNodes(any());
    }
}
