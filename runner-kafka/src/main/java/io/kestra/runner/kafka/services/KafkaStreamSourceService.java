package io.kestra.runner.kafka.services;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.services.TaskDefaultService;
import io.kestra.runner.kafka.KafkaExecutor;
import io.kestra.runner.kafka.serializers.JsonSerde;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@Slf4j
public class KafkaStreamSourceService {
    public static final String TOPIC_FLOWLAST = "flowlast";
    public static final String TOPIC_EXECUTOR = "executor";
    public static final String TOPIC_EXECUTOR_WORKERINSTANCE = "executorworkerinstance";

    @Inject
    private KafkaAdminService kafkaAdminService;

    @Inject
    private TaskDefaultService taskDefaultService;

    @Inject
    @javax.inject.Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logQueue;

    public GlobalKTable<String, Flow> flowGlobalKTable(StreamsBuilder builder) {
        return builder
            .globalTable(
                kafkaAdminService.getTopicName(Flow.class),
                Consumed.with(Serdes.String(), JsonSerde.of(Flow.class)),
                Materialized.<String, Flow, KeyValueStore<Bytes, byte[]>>as("flow")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(JsonSerde.of(Flow.class))
            );
    }

    public KStream<String, Execution> executorKStream(StreamsBuilder builder) {
        return builder
            .stream(
                kafkaAdminService.getTopicName(TOPIC_EXECUTOR),
                Consumed.with(Serdes.String(), JsonSerde.of(Execution.class))
            );
    }

    public KTable<String, Execution> executorKTable(StreamsBuilder builder) {
        return builder
            .table(
                kafkaAdminService.getTopicName(TOPIC_EXECUTOR),
                Consumed.with(Serdes.String(), JsonSerde.of(Execution.class)),
                Materialized.<String, Execution, KeyValueStore<Bytes, byte[]>>as("execution")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(JsonSerde.of(Execution.class))
            );
    }

    public GlobalKTable<String, Execution> executorGlobalKTable(StreamsBuilder builder) {
        return builder
            .globalTable(
                kafkaAdminService.getTopicName(TOPIC_EXECUTOR),
                Consumed.with(Serdes.String(), JsonSerde.of(Execution.class)),
                Materialized.<String, Execution, KeyValueStore<Bytes, byte[]>>as("execution")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(JsonSerde.of(Execution.class))
            );
    }

    public GlobalKTable<String, Trigger> triggerGlobalKTable(StreamsBuilder builder) {
        return builder
            .globalTable(
                kafkaAdminService.getTopicName(Trigger.class),
                Consumed.with(Serdes.String(), JsonSerde.of(Trigger.class)),
                Materialized.<String, Trigger, KeyValueStore<Bytes, byte[]>>as("trigger")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(JsonSerde.of(Trigger.class))
            );
    }

    public KStream<String, KafkaExecutor.ExecutionWithFlow> withFlow(GlobalKTable<String, Flow> flowGlobalKTable, KStream<String, Execution> executionKStream, boolean injectDefaults) {
        return executionKStream
            .filter(
                (key, value) -> value != null, Named.as("withFlow-notNull-filter"))
            .join(
                flowGlobalKTable,
                (key, value) -> Flow.uid(value),
                (execution, flow) -> {
                    if (!injectDefaults) {
                        return new KafkaExecutor.ExecutionWithFlow(flow, execution);
                    }

                    Flow flowWithDefaults = taskDefaultService.injectDefaults(flow, execution);
                    return new KafkaExecutor.ExecutionWithFlow(flowWithDefaults, execution);
                },
                Named.as("withFlow-join")
            );
    }


    public static <T> KStream<String, T> logIfEnabled(KStream<String, T> stream, ForeachAction<String, T> action, String name) {
        if (log.isDebugEnabled()) {
            return stream
                .filter((key, value) -> value != null, Named.as(name + "-null-filter"))
                .peek(action, Named.as(name + "-peek"));
        } else {
            return stream;
        }
    }
}
