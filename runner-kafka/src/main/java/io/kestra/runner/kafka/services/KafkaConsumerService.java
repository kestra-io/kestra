package io.kestra.runner.kafka.services;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.runner.kafka.ConsumerInterceptor;
import io.kestra.runner.kafka.configs.ClientConfig;
import io.kestra.runner.kafka.configs.ConsumerDefaultsConfig;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@Slf4j
public class KafkaConsumerService {
    @Inject
    private ClientConfig clientConfig;

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private ConsumerDefaultsConfig consumerConfig;

    @Inject
    private KafkaConfigService kafkaConfigService;

    @Inject
    private MetricRegistry metricRegistry;

    @Value("${kestra.server.metrics.kafka.consumer:true}")
    protected Boolean metricsEnabled;

    public <V> org.apache.kafka.clients.consumer.Consumer<String, V> of(Class<?> clientId, Serde<V> serde, Class<?> group) {
        return of(clientId, serde, null, group);
    }

    public <V> org.apache.kafka.clients.consumer.Consumer<String, V> of(
        Class<?> clientId,
        Serde<V> serde,
        ConsumerRebalanceListener consumerRebalanceListener,
        Class<?> group
    ) {
        Properties props = new Properties();
        props.putAll(clientConfig.getProperties());

        if (this.consumerConfig.getProperties() != null) {
            props.putAll(consumerConfig.getProperties());
        }

        props.put(CommonClientConfigs.CLIENT_ID_CONFIG, clientId.getName());
        props.put(KafkaStreamService.APPLICATION_CONTEXT_CONFIG, applicationContext);

        if (group != null) {
            props.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaConfigService.getConsumerGroupName(group));
        } else {
            props.remove(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG);
        }

        // interceptor
        if (clientConfig.getLoggers() != null) {
            props.put(
                ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG,
                ConsumerInterceptor.class.getName()
            );
        }

        return new Consumer<>(props, serde, metricsEnabled ? metricRegistry : null, consumerRebalanceListener);
    }

    public static <T> Map<TopicPartition, OffsetAndMetadata> maxOffsets(ConsumerRecords<String, T> records) {
        return KafkaConsumerService.maxOffsets(
            StreamSupport
                .stream(records.spliterator(), false)
                .collect(Collectors.toList())
        );
    }

    public static <T> Map<TopicPartition, OffsetAndMetadata> maxOffsets(List<ConsumerRecord<String, T>> records) {
        Map<TopicPartition, OffsetAndMetadata> results = new HashMap<>();

        for (ConsumerRecord<String, T> record: records) {
            TopicPartition topicPartition = new TopicPartition(record.topic(), record.partition());
            results.compute(topicPartition, (current, offsetAndMetadata) -> {
                if (offsetAndMetadata == null || record.offset() + 1 > offsetAndMetadata.offset()) {
                    return new OffsetAndMetadata(record.offset() + 1);
                } else {
                    return offsetAndMetadata;
                }
            });
        }

        return results;
    }

    public static class Consumer<V> extends KafkaConsumer<String, V> {
        protected Logger logger = LoggerFactory.getLogger(KafkaConsumerService.class);
        private KafkaClientMetrics metrics;
        private final ConsumerRebalanceListener consumerRebalanceListener;

        private Consumer(Properties properties, Serde<V> valueSerde, MetricRegistry meterRegistry, ConsumerRebalanceListener consumerRebalanceListener) {
            super(properties, new StringDeserializer(), valueSerde.deserializer());

            if (meterRegistry != null) {
                metrics = new KafkaClientMetrics(
                    this,
                    List.of(
                        Tag.of("client_type", "consumer"),
                        Tag.of("client_class_id", (String) properties.get(CommonClientConfigs.CLIENT_ID_CONFIG))
                    )
                );
                meterRegistry.bind(metrics);
            }

            this.consumerRebalanceListener = consumerRebalanceListener;
        }

        @Override
        public void subscribe(Collection<String> topics) {
            super.subscribe(topics, new ConsumerRebalanceListener() {
                @Override
                public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                    if (consumerRebalanceListener != null) {
                        consumerRebalanceListener.onPartitionsRevoked(partitions);
                    }

                    if (log.isTraceEnabled()) {
                        partitions.forEach(topicPartition -> logger.trace(
                            "Revoke partitions for topic {}, partition {}",
                            topicPartition.topic(),
                            topicPartition.partition()
                        ));
                    }
                }

                @Override
                public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                    if (consumerRebalanceListener != null) {
                        consumerRebalanceListener.onPartitionsAssigned(partitions);
                    }

                    if (log.isTraceEnabled()) {
                        partitions.forEach(topicPartition -> logger.trace(
                            "Switching partitions for topic {}, partition {}",
                            topicPartition.topic(),
                            topicPartition.partition()
                        ));
                    }
                }
            });
        }

        @Override
        public void close() {
            if (metrics != null) {
                metrics.close();
            }

            super.close();
        }

        @Override
        public void close(Duration timeout) {
            if (metrics != null) {
                metrics.close();
            }

            super.close(timeout);
        }
    }
}
