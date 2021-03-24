package io.kestra.runner.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.schedulers.AbstractScheduler;
import io.kestra.core.schedulers.AbstractSchedulerTest;
import io.kestra.runner.kafka.configs.TopicsConfig;
import io.kestra.runner.kafka.serializers.JsonSerde;
import io.kestra.runner.kafka.services.KafkaAdminService;
import io.kestra.runner.kafka.services.KafkaProducerService;
import io.kestra.runner.kafka.services.KafkaStreamSourceService;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class KafkaSchedulerTest extends AbstractSchedulerTest {
    @Inject
    protected KafkaFlowListeners flowListeners;

    @Inject
    protected KafkaAdminService kafkaAdminService;

    @Inject
    protected FlowRepositoryInterface flowRepositoryInterface;

    protected KafkaQueue<Execution> executorQueue;
    protected KafkaQueue<Trigger> triggerQueue;
    protected KafkaProducer<String, Execution> executorProducer;
    private TopicsConfig topicsConfig;

    @BeforeEach
    private void init() {
        this.executorQueue = new KafkaQueue<>(KafkaStreamSourceService.TOPIC_EXECUTOR, Execution.class, applicationContext);
        this.triggerQueue = new KafkaQueue<>(Trigger.class, applicationContext);
        this.executorProducer = applicationContext.getBean(KafkaProducerService.class).of(Execution.class, JsonSerde.of(Execution.class));
        this.topicsConfig = KafkaQueue.topicsConfig(applicationContext, KafkaStreamSourceService.TOPIC_EXECUTOR);
    }

    private static Flow createThreadFlow() {
        UnitTest schedule = UnitTest.builder()
            .id("sleep")
            .type(UnitTest.class.getName())
            .build();

        return createFlow(Collections.singletonList(schedule));
    }

    @Test
    void thread() throws Exception {
        // mock flow listeners
        CountDownLatch queueCount = new CountDownLatch(2);

        Flow flow = createThreadFlow();

        flowRepositoryInterface.create(flow);

        // scheduler
        try (AbstractScheduler scheduler = new KafkaScheduler(
            applicationContext,
            flowListeners
        )) {

            AtomicReference<Execution> last = new AtomicReference<>();

            // wait for execution
            executionQueue.receive(KafkaSchedulerTest.class, execution -> {
                last.set(execution);
                if (execution.getState().getCurrent() == State.Type.CREATED) {
                    executorQueue.emit(execution.withState(State.Type.SUCCESS));
                    queueCount.countDown();
                } else if (execution.getState().getCurrent() == State.Type.SUCCESS) {
                    executorProducer.send(new ProducerRecord<>(
                        this.topicsConfig.getName(),
                        execution.getId(),
                        null
                    ));
                }

                assertThat(execution.getFlowId(), is(flow.getId()));
            });

            triggerQueue.receive(trigger -> {
                triggerQueue.emit(trigger.resetExecution());
            });

            scheduler.run();
            queueCount.await(60, TimeUnit.SECONDS);

            assertThat(last.get().getVariables().get("counter"), is(3));
        }
    }
}
