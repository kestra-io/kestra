package io.kestra.runner.kafka.executors;

import io.kestra.runner.kafka.KafkaQueueEnabled;
import io.kestra.runner.kafka.services.KafkaStreamService;
import io.micronaut.context.ApplicationContext;
import org.apache.kafka.streams.StreamsBuilder;

@KafkaQueueEnabled
public interface KafkaExecutorInterface {
    StreamsBuilder topology();

    default void onCreated(ApplicationContext applicationContext, KafkaStreamService.Stream stream) {
        // no op
    }
}
