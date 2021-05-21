package io.kestra.runner.kafka.services;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.services.TaskDefaultService;
import io.kestra.runner.kafka.KafkaExecutor;
import io.kestra.runner.kafka.serializers.JsonSerde;
import lombok.AllArgsConstructor;
import lombok.Getter;
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

    public GlobalKTable<String, Flow> flowGlobalKTable(StreamsBuilder builder) {
        return builder
            .globalTable(
                kafkaAdminService.getTopicName(Flow.class),
                Consumed.with(Serdes.String(), JsonSerde.of(Flow.class)).withName("GlobalKTable.Flow"),
                Materialized.<String, Flow, KeyValueStore<Bytes, byte[]>>as("flow")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(JsonSerde.of(Flow.class))
            );
    }

    public KStream<String, Execution> executorKStream(StreamsBuilder builder) {
        return builder
            .stream(
                kafkaAdminService.getTopicName(TOPIC_EXECUTOR),
                Consumed.with(Serdes.String(), JsonSerde.of(Execution.class)).withName("KStream.Executor")
            );
    }

    public KTable<String, KafkaExecutor.Executor> executorKTable(StreamsBuilder builder) {
        return builder
            .table(
                kafkaAdminService.getTopicName(TOPIC_EXECUTOR),
                Consumed.with(Serdes.String(), JsonSerde.of(Execution.class)).withName("KTable.Executor"),
                Materialized.<String, Execution, KeyValueStore<Bytes, byte[]>>as("execution")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(JsonSerde.of(Execution.class))
            )
            .mapValues((readOnlyKey, value) -> new KafkaExecutor.Executor(value));
    }

    public GlobalKTable<String, Execution> executorGlobalKTable(StreamsBuilder builder) {
        return builder
            .globalTable(
                kafkaAdminService.getTopicName(TOPIC_EXECUTOR),
                Consumed.with(Serdes.String(), JsonSerde.of(Execution.class)).withName("GlobalKTable.Executor"),
                Materialized.<String, Execution, KeyValueStore<Bytes, byte[]>>as("execution")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(JsonSerde.of(Execution.class))
            );
    }

    public GlobalKTable<String, Trigger> triggerGlobalKTable(StreamsBuilder builder) {
        return builder
            .globalTable(
                kafkaAdminService.getTopicName(Trigger.class),
                Consumed.with(Serdes.String(), JsonSerde.of(Trigger.class)).withName("GlobalKTable.Trigger"),
                Materialized.<String, Trigger, KeyValueStore<Bytes, byte[]>>as("trigger")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(JsonSerde.of(Trigger.class))
            );
    }

    public KStream<String, KafkaExecutor.ExecutorWithFlow> executorWithFlow(GlobalKTable<String, Flow> flowGlobalKTable, KStream<String, KafkaExecutor.Executor> executionKStream) {
        return executionKStream
            .filter(
                (key, value) -> value != null, Named.as("ExecutorWithFlow.notNullFilter"))
            .join(
                flowGlobalKTable,
                (key, executor) -> Flow.uid(executor.getExecution()),
                (executor, flow) -> {
                    Flow flowWithDefaults = taskDefaultService.injectDefaults(flow, executor.getExecution());
                    return new KafkaExecutor.ExecutorWithFlow(executor, flowWithDefaults);
                },
                Named.as("ExecutorWithFlow.join")
            );
    }

    public KStream<String, ExecutorWithFlow> withFlow(GlobalKTable<String, Flow> flowGlobalKTable, KStream<String, Execution> executionKStream) {
        return executionKStream
            .filter(
                (key, value) -> value != null, Named.as("WithFlow.filterNotNull"))
            .join(
                flowGlobalKTable,
                (key, value) -> Flow.uid(value),
                (execution, flow) -> new ExecutorWithFlow(flow, execution),
                Named.as("WithFlow.join")
            );
    }

    public static <T> KStream<String, T> logIfEnabled(KStream<String, T> stream, ForeachAction<String, T> action, String name) {
        if (log.isDebugEnabled()) {
            return stream
                .filter((key, value) -> value != null, Named.as(name + "Log.filterNotNull"))
                .peek(action, Named.as(name + "Log.peek"));
        } else {
            return stream;
        }
    }

    @AllArgsConstructor
    @Getter
    public static class ExecutorWithFlow {
        Flow flow;
        Execution execution;
    }
}
