package io.kestra.runner.kafka.executors;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.templates.Template;
import io.kestra.runner.kafka.KafkaFlowExecutor;
import io.kestra.runner.kafka.KafkaQueueEnabled;
import io.kestra.runner.kafka.KafkaTemplateExecutor;
import io.kestra.runner.kafka.serializers.JsonSerde;
import io.kestra.runner.kafka.services.KafkaAdminService;
import io.kestra.runner.kafka.services.KafkaStreamsBuilder;
import io.kestra.runner.kafka.streams.GlobalInMemoryStateProcessor;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.state.Stores;

@KafkaQueueEnabled
@Singleton
@Slf4j
public class ExecutorStore implements KafkaExecutorInterface {
    public static final String FLOW_STATE_STORE_NAME = "flow";
    public static final String TEMPLATE_STATE_STORE_NAME = "template";

    @Inject
    private KafkaAdminService kafkaAdminService;

    @Inject
    private KafkaFlowExecutor kafkaFlowExecutor;

    @Inject
    private KafkaTemplateExecutor kafkaTemplateExecutor;

    public StreamsBuilder topology() {
        StreamsBuilder builder = new KafkaStreamsBuilder();

        builder.addGlobalStore(
            Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(FLOW_STATE_STORE_NAME),
                Serdes.String(),
                JsonSerde.of(Flow.class)
            ),
            kafkaAdminService.getTopicName(Flow.class),
            Consumed.with(Serdes.String(), JsonSerde.of(Flow.class)).withName("GlobalStore.Flow"),
            () -> new GlobalInMemoryStateProcessor<>(
                FLOW_STATE_STORE_NAME,
                flows -> kafkaFlowExecutor.setFlows(flows)
            )
        );

        builder.addGlobalStore(
            Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(TEMPLATE_STATE_STORE_NAME),
                Serdes.String(),
                JsonSerde.of(Template.class)
            ),
            kafkaAdminService.getTopicName(Template.class),
            Consumed.with(Serdes.String(), JsonSerde.of(Template.class)).withName("GlobalStore.Template"),
            () -> new GlobalInMemoryStateProcessor<>(
                TEMPLATE_STATE_STORE_NAME,
                templates -> kafkaTemplateExecutor.setTemplates(templates)
            )
        );

        return builder;
    }
}
