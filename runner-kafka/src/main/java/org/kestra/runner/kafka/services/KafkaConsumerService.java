package org.kestra.runner.kafka.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.kestra.runner.kafka.KafkaQueue;
import org.kestra.runner.kafka.configs.ClientConfig;
import org.kestra.runner.kafka.configs.ConsumerDefaultsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.Properties;

@Singleton
@Slf4j
public class KafkaConsumerService {
    @Inject
    private ClientConfig clientConfig;

    @Inject
    private ConsumerDefaultsConfig consumerConfig;

    public <V> Consumer<V> of(Class<?> group, Serde<V> serde) {
        Properties properties = new Properties();
        properties.putAll(clientConfig.getProperties());

        if (this.consumerConfig.getProperties() != null) {
            properties.putAll(consumerConfig.getProperties());
        }

        if (group != null) {
            properties.put(CommonClientConfigs.CLIENT_ID_CONFIG, KafkaQueue.getConsumerGroupName(group));
            properties.put(ConsumerConfig.GROUP_ID_CONFIG, KafkaQueue.getConsumerGroupName(group));
        } else {
            properties.remove(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG);
        }

        return new Consumer<>(properties, serde);
    }

    public static class Consumer<V> extends KafkaConsumer<String, V> {
        protected Logger logger = LoggerFactory.getLogger(KafkaConsumerService.class);

        private Consumer(Properties properties, Serde<V> valueSerde) {
            super(properties, new StringDeserializer(), valueSerde.deserializer());
        }

        @Override
        public void subscribe(Collection<String> topics) {
            super.subscribe(topics, new ConsumerRebalanceListener() {
                @Override
                public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
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
    }
}
