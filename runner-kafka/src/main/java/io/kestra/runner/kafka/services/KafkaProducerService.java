package io.kestra.runner.kafka.services;

import com.google.common.collect.ImmutableMap;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics;
import io.micronaut.context.annotation.Value;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.StringSerializer;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.runner.kafka.configs.ClientConfig;
import io.kestra.runner.kafka.configs.ProducerDefaultsConfig;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Singleton
public class KafkaProducerService {
    @Inject
    private ClientConfig clientConfig;

    @Inject
    private ProducerDefaultsConfig producerConfig;

    @Inject
    private KafkaConfigService kafkaConfigService;

    @Inject
    private MetricRegistry metricRegistry;

    @Value("${kestra.server.metrics.kafka.producer:true}")
    protected Boolean metricsEnabled;

    public <V> KafkaProducerService.Producer<V> of(Class<?> clientId, Serde<V> serde) {
        return this.of(clientId, serde, ImmutableMap.of());
    }

    public <V> KafkaProducerService.Producer<V> of(Class<?> clientId, Serde<V> serde, Map<String, String> properties) {
        Properties props = new Properties();
        props.putAll(clientConfig.getProperties());

        if (producerConfig.getProperties() != null) {
            props.putAll(producerConfig.getProperties());
        }

        props.putAll(properties);

        props.put(CommonClientConfigs.CLIENT_ID_CONFIG, clientId.getName());

        return new Producer<>(props, serde, metricsEnabled ? metricRegistry : null);
    }

    public static class Producer<V> extends KafkaProducer<String, V> {
        private KafkaClientMetrics metrics;

        private Producer(Properties properties, Serde<V> valueSerde, MetricRegistry meterRegistry) {
            super(properties, new StringSerializer(), valueSerde.serializer());

            if (metrics != null) {
                metrics = new KafkaClientMetrics(
                    this,
                    List.of(
                        Tag.of("client_type", "producer"),
                        Tag.of("client_class_id", (String) properties.get(CommonClientConfigs.CLIENT_ID_CONFIG))
                    )
                );
                meterRegistry.bind(metrics);
            }
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
