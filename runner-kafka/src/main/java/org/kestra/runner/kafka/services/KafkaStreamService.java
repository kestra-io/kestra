package org.kestra.runner.kafka.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.errors.StreamsException;
import org.kestra.core.metrics.MetricRegistry;
import org.kestra.runner.kafka.KafkaQueue;
import org.kestra.runner.kafka.configs.ClientConfig;
import org.kestra.runner.kafka.configs.StreamDefaultsConfig;
import org.kestra.runner.kafka.metrics.KafkaClientMetrics;
import org.kestra.runner.kafka.metrics.KafkaStreamsMetrics;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Properties;

@Singleton
@Slf4j
public class KafkaStreamService {
    @Inject
    @NotNull
    private ClientConfig clientConfig;

    @Inject
    private StreamDefaultsConfig streamConfig;

    @Inject
    private MetricRegistry metricRegistry;

    public KafkaStreamService.Stream of(Class<?> group, Topology topology) {
        Properties properties = new Properties();
        properties.putAll(clientConfig.getProperties());

        if (this.streamConfig.getProperties() != null) {
            properties.putAll(streamConfig.getProperties());
        }

        properties.put(CommonClientConfigs.CLIENT_ID_CONFIG, KafkaQueue.getConsumerGroupName(group));
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, KafkaQueue.getConsumerGroupName(group));

        Stream stream = new Stream(topology, properties);

        metricRegistry.bind(new KafkaStreamsMetrics(stream));

        return stream;
    }

    public static class Stream extends KafkaStreams {
        private Stream(Topology topology, Properties props) {
            super(topology, props);
        }

        @Override
        public synchronized void start() throws IllegalStateException, StreamsException {
            this.setUncaughtExceptionHandler((thread, e) -> {
                log.error("Uncaught exception in Kafka Stream " + thread.getName() + ", closing !", e);
                System.exit(1);
            });

            if (log.isTraceEnabled()) {
                this.setStateListener((newState, oldState) -> {
                    log.trace("Switching stream state from {} to {}", oldState, newState);
                });
            }

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                this.close(Duration.ofSeconds(10));
            }));

            super.start();
        }
    }
}
