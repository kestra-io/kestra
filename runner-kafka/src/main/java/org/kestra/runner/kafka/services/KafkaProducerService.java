package org.kestra.runner.kafka.services;

import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.StringSerializer;
import org.kestra.core.metrics.MetricRegistry;
import org.kestra.runner.kafka.KafkaQueue;
import org.kestra.runner.kafka.configs.ClientConfig;
import org.kestra.runner.kafka.configs.ProducerDefaultsConfig;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.Properties;

@Singleton
public class KafkaProducerService {
    @Inject
    private ClientConfig clientConfig;

    @Inject
    private ProducerDefaultsConfig producerConfig;

    @Inject
    private MetricRegistry metricRegistry;

    public <V> KafkaProducerService.Producer<V> of(Class<?> name, Serde<V> serde) {
        Properties properties = new Properties();
        properties.putAll(clientConfig.getProperties());

        if (producerConfig.getProperties() != null) {
            properties.putAll(producerConfig.getProperties());
        }

        properties.put(CommonClientConfigs.CLIENT_ID_CONFIG, KafkaQueue.getConsumerGroupName(name));

        return new Producer<>(properties, serde, metricRegistry);
    }

    public static class Producer<V> extends KafkaProducer<String, V> {
        private final KafkaClientMetrics metrics;

        private Producer(Properties properties, Serde<V> valueSerde, MetricRegistry meterRegistry) {
            super(properties, new StringSerializer(), valueSerde.serializer());

            metrics = new KafkaClientMetrics(this);
            meterRegistry.bind(metrics);
        }

        @Override
        public void close() {
            metrics.close();
            super.close();
        }

        @Override
        public void close(Duration timeout) {
            metrics.close();
            super.close(timeout);
        }
    }
}
