package io.kestra.core.runners;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.flows.FlowWithSource;

import static org.assertj.core.api.Assertions.assertThat;

class FlowWithDefaultCacheTest {
    private FlowWithDefaultCache cache;

    @BeforeEach
    void setUp() {
        cache = new FlowWithDefaultCache();
    }

    @Test
    void shouldReturnEmptyOnCacheMiss() {
        assertThat(cache.getIfPresent("unknown-key")).isEmpty();
    }

    @Test
    void shouldReturnValueOnCacheHit() {
        FlowWithSource flow = flush("tenant", "ns", "flow-a", 1);
        cache.put(flow.uid(), flow);

        assertThat(cache.getIfPresent(flow.uid())).contains(flow);
    }

    @Test
    void shouldInvalidateByKey() {
        FlowWithSource flow = flush("tenant", "ns", "flow-a", 1);
        cache.put(flow.uid(), flow);

        cache.invalidate(flow.uid());

        assertThat(cache.getIfPresent(flow.uid())).isEmpty();
    }

    @Test
    void shouldFlushAllEntriesForTenant() {
        FlowWithSource flowA = flush("tenant-1", "ns", "flow-a", 1);
        FlowWithSource flowB = flush("tenant-1", "ns", "flow-b", 1);
        FlowWithSource flowC = flush("tenant-2", "ns", "flow-c", 1);
        cache.put(flowA.uid(), flowA);
        cache.put(flowB.uid(), flowB);
        cache.put(flowC.uid(), flowC);

        cache.flush("tenant-1");

        assertThat(cache.getIfPresent(flowA.uid())).isEmpty();
        assertThat(cache.getIfPresent(flowB.uid())).isEmpty();
        assertThat(cache.getIfPresent(flowC.uid())).contains(flowC);
    }

    @Test
    void shouldFlushNullTenantEntries() {
        FlowWithSource flowNull = flush(null, "ns", "flow-a", 1);
        FlowWithSource flowOther = flush("tenant-1", "ns", "flow-b", 1);
        cache.put(flowNull.uid(), flowNull);
        cache.put(flowOther.uid(), flowOther);

        cache.flush(null);

        assertThat(cache.getIfPresent(flowNull.uid())).isEmpty();
        assertThat(cache.getIfPresent(flowOther.uid())).contains(flowOther);
    }

    @Test
    void shouldExpireAllFlowsInNamespace() {
        FlowWithSource flowA = flush("tenant-1", "ns-a", "flow-a", 1);
        FlowWithSource flowB = flush("tenant-1", "ns-a", "flow-b", 1);
        FlowWithSource flowC = flush("tenant-1", "ns-b", "flow-c", 1);
        cache.put(flowA.uid(), flowA);
        cache.put(flowB.uid(), flowB);
        cache.put(flowC.uid(), flowC);

        cache.flush("tenant-1", "ns-a");

        assertThat(cache.getIfPresent(flowA.uid())).isEmpty();
        assertThat(cache.getIfPresent(flowB.uid())).isEmpty();
        assertThat(cache.getIfPresent(flowC.uid())).contains(flowC);
    }

    @Test
    void shouldNotExpireFlowsOfOtherTenantInSameNamespace() {
        FlowWithSource flowT1 = flush("tenant-1", "ns", "flow-a", 1);
        FlowWithSource flowT2 = flush("tenant-2", "ns", "flow-a", 1);
        cache.put(flowT1.uid(), flowT1);
        cache.put(flowT2.uid(), flowT2);

        cache.flush("tenant-1", "ns");

        assertThat(cache.getIfPresent(flowT1.uid())).isEmpty();
        assertThat(cache.getIfPresent(flowT2.uid())).contains(flowT2);
    }

    private FlowWithSource flush(String tenantId, String namespace, String id, int revision) {
        return FlowWithSource.builder()
            .tenantId(tenantId)
            .namespace(namespace)
            .id(id)
            .revision(revision)
            .build();
    }
}
