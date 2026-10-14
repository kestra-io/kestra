package io.kestra.metrics.h2;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.queues.VNodeDispatchQueueInterface;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.WorkerJobEvent;
import io.kestra.core.scheduler.events.TriggerEvent;
import io.kestra.core.utils.IdUtils;
import io.kestra.queue.jdbc.client.JdbcQueueClient;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@MicronautTest(transactional = false)
@Property(name = "datasources.h2.url", value = "jdbc:h2:mem:query_metrics;TIME ZONE=UTC;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@Property(name = "kestra.metrics.query.enabled", value = "true")
@Property(name = "kestra.metrics.query.interval", value = "100ms")
@Property(name = "kestra.queue.jdbc.type", value = "h2")
@Property(name = "kestra.queue.jdbc.url", value = "jdbc:h2:mem:query_metrics_queue;TIME ZONE=UTC;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@Property(name = "kestra.queue.jdbc.username", value = "sa")
class H2QueryMetricsTest {
    @Inject
    ExecutionRepositoryInterface executionRepository;

    @Inject
    JdbcQueueClient queueClient;

    @Inject
    DispatchQueueInterface<Execution> executionQueue;

    @Inject
    KeyedDispatchQueueInterface<WorkerJobEvent> workerQueue;

    @Inject
    VNodeDispatchQueueInterface<TriggerEvent> triggerQueue;

    @Inject
    BroadcastQueueInterface<FlowInterface> flowQueue;

    @Inject
    MetricRegistry metricRegistry;

    @Test
    void shouldReportPersistedExecutionAndQueueBacklogs() throws Exception {
        var created = execution(State.Type.CREATED);
        var queued = execution(State.Type.QUEUED);
        var completed = execution(State.Type.SUCCESS);
        var deleted = execution(State.Type.CREATED);
        for (var execution : List.of(created, queued, completed, deleted)) {
            executionRepository.save(execution);
        }
        executionRepository.delete(deleted);
        queueClient.publish(executionQueue.queueName(), null, created.getId(), "{}");
        queueClient.publish(workerQueue.queueName(), null, "default", "{}");
        queueClient.publish(workerQueue.queueName(), "worker-a", "first", "{}");
        queueClient.publish(workerQueue.queueName(), "worker-b", "second", "{}");
        queueClient.publish(triggerQueue.queueName(), "vnode_0", "trigger", "{}");
        flowQueue.emit(FlowWithSource.builder().id("backlog").namespace("io.kestra.query.metrics").tasks(List.of()).build());

        assertThat(executionRepository.findById("main", created.getId())).isPresent();
        assertThat(queueClient.queueLag(executionQueue.queueName(), null)).isEqualTo(1);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
        {
            assertThat(executionBacklog(State.Type.CREATED)).isEqualTo(1);
            assertThat(executionBacklog(State.Type.QUEUED)).isEqualTo(1);
            assertThat(queueBacklog(executionQueue.queueName())).isEqualTo(1);
            assertThat(queueBacklog(workerQueue.queueName())).isEqualTo(3);
            assertThat(queueBacklog(triggerQueue.queueName())).isEqualTo(1);
            assertThat(metricRegistry.findGauges("queue.message.pending"))
                .noneMatch(gauge -> flowQueue.queueName().equals(gauge.getId().getTag(MetricRegistry.TAG_QUEUE_NAME)));
        });

        executionRepository.save(created.withState(State.Type.RUNNING));
        executionRepository.save(queued.withState(State.Type.SUCCESS));
        queueClient.subscribeDispatch(executionQueue.queueName(), null, message ->
        {
        });
        queueClient.subscribeDispatch(workerQueue.queueName(), null, message ->
        {
        });
        queueClient.subscribeDispatch(workerQueue.queueName(), List.of("worker-a", "worker-b"), message ->
        {
        });
        queueClient.subscribeDispatch(triggerQueue.queueName(), List.of("vnode_0"), message ->
        {
        });

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
        {
            assertThat(executionBacklog(State.Type.CREATED)).isZero();
            assertThat(executionBacklog(State.Type.QUEUED)).isZero();
            assertThat(queueBacklog(executionQueue.queueName())).isZero();
            assertThat(queueBacklog(workerQueue.queueName())).isZero();
            assertThat(queueBacklog(triggerQueue.queueName())).isZero();
        });
    }

    private Execution execution(State.Type state) {
        return Execution.builder()
            .id(IdUtils.create())
            .tenantId("main")
            .namespace("io.kestra.query.metrics")
            .flowId("backlog")
            .flowRevision(1)
            .state(new State(state))
            .build();
    }

    private double executionBacklog(State.Type state) {
        var gauge = metricRegistry.findGauges("executor.execution.pending").stream()
            .filter(metric -> state.name().equals(metric.getId().getTag(MetricRegistry.TAG_STATE)))
            .findFirst();
        assertThat(gauge).as("Execution backlog gauge for %s", state).isPresent();
        return gauge.orElseThrow().value();
    }

    private double queueBacklog(String queueName) {
        var gauge = metricRegistry.findGauges("queue.message.pending").stream()
            .filter(metric -> queueName.equals(metric.getId().getTag(MetricRegistry.TAG_QUEUE_NAME)))
            .findFirst();
        assertThat(gauge).as("Pending JDBC messages gauge").isPresent();
        return gauge.orElseThrow().value();
    }
}
