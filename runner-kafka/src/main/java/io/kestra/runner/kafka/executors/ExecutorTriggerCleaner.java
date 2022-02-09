package io.kestra.runner.kafka.executors;

import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.queues.QueueService;
import io.kestra.core.runners.Executor;
import io.kestra.core.services.ConditionService;
import io.kestra.runner.kafka.KafkaQueueEnabled;
import io.kestra.runner.kafka.serializers.JsonSerde;
import io.kestra.runner.kafka.services.KafkaAdminService;
import io.kestra.runner.kafka.services.KafkaStreamSourceService;
import io.kestra.runner.kafka.services.KafkaStreamsBuilder;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.GlobalKTable;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;

@KafkaQueueEnabled
@Singleton
@Slf4j
public class ExecutorTriggerCleaner implements KafkaExecutorInterface {
    @Inject
    private KafkaAdminService kafkaAdminService;

    @Inject
    private KafkaStreamSourceService kafkaStreamSourceService;

    @Inject
    private QueueService queueService;

    @Inject
    private ConditionService conditionService;

    public StreamsBuilder topology() {
        StreamsBuilder builder = new KafkaStreamsBuilder();

        KStream<String, Executor> executorKStream = kafkaStreamSourceService.executorKStream(builder);

        KStream<String, Executor> executionWithFlowKStream = kafkaStreamSourceService.executorWithFlow(executorKStream, false);

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

        return builder;
    }
}
