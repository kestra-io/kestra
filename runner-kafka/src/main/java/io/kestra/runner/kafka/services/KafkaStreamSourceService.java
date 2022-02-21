package io.kestra.runner.kafka.services;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.runners.Executor;
import io.kestra.core.runners.FlowExecutorInterface;
import io.kestra.core.services.TaskDefaultService;
import io.kestra.core.tasks.flows.Template;
import io.kestra.core.utils.Await;
import io.kestra.runner.kafka.serializers.JsonSerde;
import io.kestra.runner.kafka.streams.FlowJoinerTransformer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@Singleton
@Slf4j
public class KafkaStreamSourceService {
    public static final String TOPIC_FLOWLAST = "flowlast";
    public static final String TOPIC_EXECUTOR_WORKERINSTANCE = "executorworkerinstance";

    @Inject
    private KafkaAdminService kafkaAdminService;

    @Inject
    private TaskDefaultService taskDefaultService;

    @Inject
    private FlowExecutorInterface flowExecutorInterface;

    @Inject
    private Template.TemplateExecutorInterface templateExecutorInterface;

    public KStream<String, Executor> executorKStream(StreamsBuilder builder) {
        return builder
            .stream(
                kafkaAdminService.getTopicName(Executor.class),
                Consumed.with(Serdes.String(), JsonSerde.of(Executor.class)).withName("KStream.Executor")
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

    public KStream<String, Executor> executorWithFlow(KStream<String, Executor> executionKStream, boolean withDefaults) {
        return executionKStream
            .filter((key, value) -> value != null, Named.as("ExecutorWithFlow.filterNotNull"))
            .transformValues(
                () -> new FlowJoinerTransformer(this, withDefaults)
            );
    }

    public Executor joinFlow(Executor executor, Boolean withDefaults) {
        Flow flow;

        try {
            // pooling of new flow can be delayed on ExecutorStore, we maybe need to wait that the flow is updated
            flow = Await.until(
                () -> flowExecutorInterface.findByExecution(executor.getExecution()).orElse(null),
                Duration.ofMillis(100),
                Duration.ofMinutes(5)
            );
        } catch (TimeoutException e) {
            return executor.withException(
                new Exception("Unable to find flow with namespace: '" + executor.getExecution().getNamespace() + "'" +
                    ", id: '" + executor.getExecution().getFlowId() + "', " +
                    "revision '" + executor.getExecution().getFlowRevision() + "'"),
                "joinFlow"
            );
        }

        if (!withDefaults) {
            return executor.withFlow(flow);
        }

        try {
            flow = Template.injectTemplate(
                flow,
                executor.getExecution(),
                (namespace, id) -> this.templateExecutorInterface.findById(namespace, id).orElse(null)
            );
        } catch (InternalException e) {
            log.warn("Failed to inject template",  e);
        }

        Flow flowWithDefaults = taskDefaultService.injectDefaults(flow, executor.getExecution());

        return executor.withFlow(flowWithDefaults);
    }

    public static <T> KStream<String, T> logIfEnabled(Logger log, KStream<String, T> stream, ForeachAction<String, T> action, String name) {
        if (log.isDebugEnabled()) {
            return stream
                .filter((key, value) -> value != null, Named.as(name + "Log.filterNotNull"))
                .peek(action, Named.as(name + "Log.peek"));
        } else {
            return stream;
        }
    }
}
