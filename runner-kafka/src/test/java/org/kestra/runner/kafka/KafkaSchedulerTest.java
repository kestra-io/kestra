package org.kestra.runner.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.State;
import org.kestra.core.schedulers.AbstractScheduler;
import org.kestra.core.schedulers.AbstractSchedulerTest;
import org.kestra.runner.kafka.configs.TopicsConfig;
import org.kestra.runner.kafka.serializers.JsonSerde;
import org.kestra.runner.kafka.services.KafkaAdminService;
import org.kestra.runner.kafka.services.KafkaProducerService;
import org.kestra.runner.kafka.services.KafkaStreamSourceService;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class KafkaSchedulerTest extends AbstractSchedulerTest {
    @Inject
    protected KafkaFlowListeners flowListeners;

    @Inject
    protected KafkaAdminService kafkaAdminService;

    protected KafkaQueue<Execution> executorQueue;
    protected KafkaProducer<String, Execution> executorProducer;
    private TopicsConfig topicsConfig;

    @BeforeEach
    private void init() {
        this.executorQueue = new KafkaQueue<>(KafkaStreamSourceService.TOPIC_EXECUTOR, Execution.class, applicationContext);
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
        KafkaFlowListeners flowListenersServiceSpy = spy(this.flowListeners);
        CountDownLatch queueCount = new CountDownLatch(2);

        Flow flow = createThreadFlow();

        doReturn(Collections.singletonList(flow))
            .when(flowListenersServiceSpy)
            .flows();

        // scheduler
        try (AbstractScheduler scheduler = new KafkaScheduler(
            applicationContext,
            flowListenersServiceSpy
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

            scheduler.run();
            queueCount.await();

            assertThat(last.get().getVariables().get("counter"), is(3));
        }
    }
}
