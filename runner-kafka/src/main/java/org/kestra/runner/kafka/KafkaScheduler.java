package org.kestra.runner.kafka;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.inject.qualifiers.Qualifiers;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.Stores;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.triggers.Trigger;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.queues.QueueService;
import org.kestra.core.schedulers.AbstractScheduler;
import org.kestra.core.schedulers.DefaultScheduler;
import org.kestra.core.schedulers.SchedulerExecutionWithTrigger;
import org.kestra.core.services.ConditionService;
import org.kestra.core.services.FlowListenersInterface;
import org.kestra.runner.kafka.serializers.JsonSerde;
import org.kestra.runner.kafka.services.KafkaAdminService;
import org.kestra.runner.kafka.services.KafkaStreamService;
import org.kestra.runner.kafka.services.KafkaStreamSourceService;
import org.kestra.runner.kafka.streams.GlobalStateLockProcessor;
import org.kestra.runner.kafka.streams.GlobalStateProcessor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@KafkaQueueEnabled
@Prototype
@Slf4j
@Replaces(DefaultScheduler.class)
public class KafkaScheduler extends AbstractScheduler {
    private static final String STATESTORE_EXECUTOR = "schedulerexecutor";
    private static final String STATESTORE_TRIGGER = "schedulertrigger";

    private final KafkaAdminService kafkaAdminService;
    private final KafkaStreamService kafkaStreamService;
    private final QueueInterface<Trigger> triggerQueue;
    private final ConditionService conditionService;
    private final KafkaStreamSourceService kafkaStreamSourceService;
    private final QueueService queueService;

    private Map<String, Trigger> triggerLock = new ConcurrentHashMap<>();

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
        this.conditionService = applicationContext.getBean(ConditionService.class);
        this.kafkaStreamSourceService = applicationContext.getBean(KafkaStreamSourceService.class);
        this.queueService = applicationContext.getBean(QueueService.class);
    }

    public class SchedulerCleaner {
        private Topology topology() {
            StreamsBuilder builder = new StreamsBuilder();

            KStream<String, Execution> executorKStream = kafkaStreamSourceService.executorKStream(builder);
            GlobalKTable<String, Flow> flowKTable = kafkaStreamSourceService.flowGlobalKTable(builder);
            KStream<String, KafkaExecutor.ExecutionWithFlow> executionWithFlowKStream = kafkaStreamSourceService.withFlow(
                flowKTable,
                executorKStream
            );
            GlobalKTable<String, Trigger> triggerGlobalKTable = kafkaStreamSourceService.triggerGlobalKTable(builder);

            executionWithFlowKStream
                .filter(
                    (key, value) -> value.getExecution().getTrigger() != null,
                    Named.as("cleanTrigger-hasTrigger-filter")
                )
                .filter(
                    (key, value) -> conditionService.isTerminatedWithListeners(value.getFlow(), value.getExecution()),
                    Named.as("cleanTrigger-terminated-filter")
                )
                .join(
                    triggerGlobalKTable,
                    (key, executionWithFlow) -> Trigger.uid(executionWithFlow.getExecution()),
                    (execution, trigger) -> trigger.resetExecution(),
                    Named.as("cleanTrigger-join")
                )
                .selectKey((key, value) -> queueService.key(value))
                .to(
                    kafkaAdminService.getTopicName(Trigger.class),
                    Produced.with(Serdes.String(), JsonSerde.of(Trigger.class))
                );

            // build
            Topology topology = builder.build();

            if (log.isTraceEnabled()) {
                log.trace(topology.describe().toString());
            }

            return topology;
        }
    }

    public class SchedulerState {
        public Topology topology() {
            StreamsBuilder builder = new StreamsBuilder();

            // executor global state store
            builder.addGlobalStore(
                Stores.keyValueStoreBuilder(
                    Stores.persistentKeyValueStore(STATESTORE_EXECUTOR),
                    Serdes.String(),
                    JsonSerde.of(Execution.class)
                ),
                kafkaAdminService.getTopicName(KafkaStreamSourceService.TOPIC_EXECUTOR),
                Consumed.with(Serdes.String(), JsonSerde.of(Execution.class)),
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
                Consumed.with(Serdes.String(), JsonSerde.of(Trigger.class)),
                () -> new GlobalStateLockProcessor<>(STATESTORE_TRIGGER, triggerLock)
            );

            // build
            Topology topology = builder.build();

            if (log.isTraceEnabled()) {
                log.trace(topology.describe().toString());
            }

            return topology;
        }
    }

    /**
     * We saved the trigger in a local hash map that will be clean by {@link GlobalStateProcessor}.
     * The scheduler trust the STATESTORE_TRIGGER to know if a running execution exists. Since the store is filled async,
     * this can lead to empty trigger and launch of concurrent job.
     *
     * @param executionWithTrigger the execution trigger to save
     * @return Trigger saved
     */
    protected synchronized Trigger saveLastTrigger(SchedulerExecutionWithTrigger executionWithTrigger) {

        Trigger trigger = super.saveLastTrigger(executionWithTrigger);
        this.triggerLock.put(trigger.uid(), trigger);

        return trigger;
    }

    @Override
    public void run() {
        kafkaAdminService.createIfNotExist(Flow.class);
        kafkaAdminService.createIfNotExist(KafkaStreamSourceService.TOPIC_EXECUTOR);
        kafkaAdminService.createIfNotExist(Trigger.class);

        KafkaStreamService.Stream stateStream = kafkaStreamService.of(SchedulerState.class, new SchedulerState().topology());
        stateStream.start((newState, oldState) -> {
            this.isReady = newState == KafkaStreams.State.RUNNING;
        });

        this.triggerState =  new KafkaSchedulerTriggerState(
            stateStream.store(STATESTORE_TRIGGER, QueryableStoreTypes.keyValueStore()),
            triggerQueue,
            triggerLock
        );

        this.executionState = new KafkaSchedulerExecutionState(
            stateStream.store(STATESTORE_EXECUTOR, QueryableStoreTypes.keyValueStore())
        );

        KafkaStreamService.Stream cleanTriggerStream = kafkaStreamService.of(SchedulerCleaner.class, new SchedulerCleaner().topology());
        cleanTriggerStream.start();

        super.run();
    }
}
