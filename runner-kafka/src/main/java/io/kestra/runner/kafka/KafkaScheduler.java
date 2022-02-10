package io.kestra.runner.kafka;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.queues.QueueService;
import io.kestra.core.runners.Executor;
import io.kestra.core.schedulers.AbstractScheduler;
import io.kestra.core.schedulers.DefaultScheduler;
import io.kestra.core.schedulers.SchedulerExecutionWithTrigger;
import io.kestra.core.services.FlowListenersInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.runner.kafka.configs.TopicsConfig;
import io.kestra.runner.kafka.serializers.JsonSerde;
import io.kestra.runner.kafka.services.KafkaAdminService;
import io.kestra.runner.kafka.services.KafkaProducerService;
import io.kestra.runner.kafka.services.KafkaStreamService;
import io.kestra.runner.kafka.services.KafkaStreamsBuilder;
import io.kestra.runner.kafka.streams.GlobalStateLockProcessor;
import io.kestra.runner.kafka.streams.GlobalStateProcessor;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.Stores;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@KafkaQueueEnabled
@Singleton
@Slf4j
@Replaces(DefaultScheduler.class)
public class KafkaScheduler extends AbstractScheduler {
    private static final String STATESTORE_EXECUTOR = "schedulerexecutor";
    private static final String STATESTORE_TRIGGER = "schedulertrigger";

    private final KafkaAdminService kafkaAdminService;
    private final KafkaStreamService kafkaStreamService;
    private final QueueInterface<Trigger> triggerQueue;
    private final QueueService queueService;
    private final KafkaProducer<String, Object> kafkaProducer;
    private final TopicsConfig topicsConfigTrigger;
    private final TopicsConfig topicsConfigExecution;

    private final Map<String, Trigger> triggerLock = new ConcurrentHashMap<>();

    protected KafkaStreamService.Stream stateStream;
    protected KafkaStreamService.Stream cleanTriggerStream;

    @SuppressWarnings("unchecked")
    public KafkaScheduler(
        ApplicationContext applicationContext,
        FlowListenersInterface flowListenersService
    ) {
        super(
            applicationContext,
            flowListenersService
        );

        this.triggerQueue = applicationContext.getBean(QueueInterface.class, Qualifiers.byName(QueueFactoryInterface.TRIGGER_NAMED));
        this.kafkaAdminService = applicationContext.getBean(KafkaAdminService.class);
        this.kafkaStreamService = applicationContext.getBean(KafkaStreamService.class);
        this.queueService = applicationContext.getBean(QueueService.class);
        this.kafkaProducer = applicationContext.getBean(KafkaProducerService.class).of(
            KafkaScheduler.class,
            JsonSerde.of(Object.class),
            ImmutableMap.of("transactional.id", IdUtils.create())
        );
        this.topicsConfigTrigger = KafkaQueue.topicsConfig(applicationContext, Trigger.class);
        this.topicsConfigExecution = KafkaQueue.topicsConfig(applicationContext, Execution.class);

        this.kafkaProducer.initTransactions();
    }

    public class SchedulerState {
        public StreamsBuilder topology() {
            StreamsBuilder builder = new KafkaStreamsBuilder();

            // executor global state store
            builder.addGlobalStore(
                Stores.keyValueStoreBuilder(
                    Stores.persistentKeyValueStore(STATESTORE_EXECUTOR),
                    Serdes.String(),
                    JsonSerde.of(Executor.class)
                ),
                kafkaAdminService.getTopicName(Executor.class),
                Consumed.with(Serdes.String(), JsonSerde.of(Executor.class)).withName("GlobalStore.Executor"),
                () -> new GlobalStateProcessor<>(STATESTORE_EXECUTOR)
            );

            // trigger global state store
            builder.addGlobalStore(
                Stores.keyValueStoreBuilder(
                    Stores.persistentKeyValueStore(STATESTORE_TRIGGER),
                    Serdes.String(),
                    JsonSerde.of(Trigger.class)
                ),
                kafkaAdminService.getTopicName(Trigger.class),
                Consumed.with(Serdes.String(), JsonSerde.of(Trigger.class)).withName("GlobalStore.Trigger"),
                () -> new GlobalStateLockProcessor<>(STATESTORE_TRIGGER, triggerLock)
            );

            return builder;
        }
    }

    /**
     * We saved the trigger in a local hash map that will be clean by {@link GlobalStateProcessor}.
     * The scheduler trust the STATESTORE_TRIGGER to know if a running execution exists. Since the store is filled async,
     * this can lead to empty trigger and launch of concurrent job.
     *
     * @param executionWithTrigger the execution trigger to save
     */
    protected synchronized void saveLastTriggerAndEmitExecution(SchedulerExecutionWithTrigger executionWithTrigger) {
        Trigger trigger = Trigger.of(
            executionWithTrigger.getTriggerContext(),
            executionWithTrigger.getExecution()
        );

        kafkaProducer.beginTransaction();

        this.kafkaProducer.send(new ProducerRecord<>(
            topicsConfigTrigger.getName(),
            this.queueService.key(trigger),
            trigger
        ));


        this.kafkaProducer.send(new ProducerRecord<>(
            topicsConfigExecution.getName(),
            this.queueService.key(executionWithTrigger.getExecution()),
            executionWithTrigger.getExecution()
        ));

        this.triggerLock.put(trigger.uid(), trigger);

        kafkaProducer.commitTransaction();
    }

    protected KafkaStreamService.Stream init(Class<?> group, StreamsBuilder builder) {
        Topology topology = builder.build();

        if (log.isTraceEnabled()) {
            log.trace(topology.describe().toString());
        }

        return kafkaStreamService.of(this.getClass(), group, topology);
    }

    public void initStream() {
        kafkaAdminService.createIfNotExist(Flow.class);
        kafkaAdminService.createIfNotExist(Executor.class);
        kafkaAdminService.createIfNotExist(Trigger.class);

        this.stateStream = this.init(SchedulerState.class, new SchedulerState().topology());
        this.stateStream.start((newState, oldState) -> {
            this.isReady = newState == KafkaStreams.State.RUNNING;
        });

        this.triggerState =  new KafkaSchedulerTriggerState(
            stateStream.store(StoreQueryParameters.fromNameAndType(STATESTORE_TRIGGER, QueryableStoreTypes.keyValueStore())),
            triggerQueue,
            triggerLock
        );

        this.executionState = new KafkaSchedulerExecutionState(
            stateStream.store(StoreQueryParameters.fromNameAndType(STATESTORE_EXECUTOR, QueryableStoreTypes.keyValueStore()))
        );
    }

    @Override
    public void run() {
        this.initStream();
        super.run();
    }

    @Override
    public void close() {
        if (stateStream != null) {
            stateStream.close(Duration.ofSeconds(10));
        }

        if (cleanTriggerStream != null) {
            cleanTriggerStream.close(Duration.ofSeconds(10));
        }

        super.close();
    }
}
