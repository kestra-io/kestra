package io.kestra.runner.kafka.streams;

import io.kestra.core.runners.Executor;
import io.kestra.runner.kafka.services.KafkaStreamSourceService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.processor.ProcessorContext;

@Slf4j
public class FlowJoinerTransformer implements ValueTransformerWithKey<String, Executor, Executor> {
    private final boolean withDefaults;
    private final KafkaStreamSourceService kafkaStreamSourceService;

    public FlowJoinerTransformer(KafkaStreamSourceService kafkaStreamSourceService, boolean withDefaults) {
        this.kafkaStreamSourceService = kafkaStreamSourceService;
        this.withDefaults = withDefaults;
    }

    @Override
    public void init(final ProcessorContext context) {

    }

    @Override
    public Executor transform(String key, Executor executor) {
        return kafkaStreamSourceService.joinFlow(
            executor,
            this.withDefaults
        );
    }

    @Override
    public void close() {
    }
}
