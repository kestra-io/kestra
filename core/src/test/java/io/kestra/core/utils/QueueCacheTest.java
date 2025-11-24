package io.kestra.core.utils;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.plugin.core.debug.Return;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueueCacheTest {

    @Inject
    private QueueInterface<FlowInterface> flowQueue;

    @Test
    void noInitialValues() throws InterruptedException, QueueException {
        var queueCache = new QueueCache<>(flowQueue);
        queueCache.start();

        List<FlowInterface> values = queueCache.values();

        assertThat(values).isEmpty();

        var flow = createFlow();
        flowQueue.emit(flow);
        Thread.sleep(100); // make sure it receives the new flow

        values = queueCache.values();

        assertThat(values).hasSize(1);

        var result = queueCache.get(flow.uid());
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(flow.getId());

        queueCache.putIfAbsent(flow);
        values = queueCache.values();
        assertThat(values).hasSize(1);

        queueCache.put(createFlow());
        values = queueCache.values();
        assertThat(values).hasSize(2);

        queueCache.close();
    }

    @Test
    void withInitialValues() throws QueueException, InterruptedException {
        List<FlowInterface> flows = List.of(createFlow(), createFlow());
        var queueCache = new QueueCache<>(flowQueue, flows);
        queueCache.start();

        List<FlowInterface> values = queueCache.values();

        assertThat(values).hasSize(2);

        var flow = createFlow();
        flowQueue.emit(flow);
        Thread.sleep(100); // make sure it receives the new flow

        values = queueCache.values();

        assertThat(values).hasSize(3);

        var result = queueCache.get(flow.uid());
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(flow.getId());

        queueCache.close();
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