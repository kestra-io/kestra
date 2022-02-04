package io.kestra.runner.kafka;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.Executor;
import io.kestra.core.schedulers.AbstractScheduler;
import io.kestra.core.schedulers.AbstractSchedulerTest;
import io.kestra.core.schedulers.SchedulerThreadTest;
import io.kestra.runner.kafka.configs.TopicsConfig;
import io.kestra.runner.kafka.serializers.JsonSerde;
import io.kestra.runner.kafka.services.KafkaAdminService;
import io.kestra.runner.kafka.services.KafkaProducerService;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class KafkaSchedulerTest extends AbstractSchedulerTest {
    @Inject
    protected KafkaFlowListeners flowListeners;

    @Inject
    protected KafkaAdminService kafkaAdminService;

    @Inject
    protected KafkaExecutor kafkaExecutor;

    @Inject
    protected FlowRepositoryInterface flowRepositoryInterface;

    protected KafkaQueue<Executor> executorQueue;
    protected KafkaQueue<Trigger> triggerQueue;
    protected KafkaProducer<String, Execution> executorProducer;
    private TopicsConfig topicsConfig;

    @BeforeEach
    private void init() {
        this.executorQueue = new KafkaQueue<>(Executor.class, applicationContext);
        this.triggerQueue = new KafkaQueue<>(Trigger.class, applicationContext);
        this.executorProducer = applicationContext.getBean(KafkaProducerService.class).of(KafkaSchedulerTest.class, JsonSerde.of(Execution.class));
        this.topicsConfig = KafkaQueue.topicsConfig(applicationContext, Executor.class);
    }

    @Test
    void thread() throws Exception {
        // mock flow listeners
        CountDownLatch queueCount = new CountDownLatch(2);

        Flow flow = SchedulerThreadTest.createThreadFlow();

        flowRepositoryInterface.create(flow);

        kafkaExecutor.run();

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
                    executionQueue.emit(execution.withState(State.Type.SUCCESS));
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

            assertThat(last.get().getVariables().get("defaultInjected"), is("done"));
            assertThat(last.get().getVariables().get("counter"), is(3));

            AbstractSchedulerTest.COUNTER = 0;
        }
    }
}
