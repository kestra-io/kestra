package org.kestra.runner.kafka.services;

import io.micrometer.core.instrument.binder.kafka.KafkaStreamsMetrics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.errors.StreamsException;
import org.apache.kafka.streams.processor.StateRestoreListener;
import org.kestra.core.metrics.MetricRegistry;
import org.kestra.runner.kafka.configs.ClientConfig;
import org.kestra.runner.kafka.configs.StreamDefaultsConfig;

import java.time.Duration;
import java.util.Properties;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;

@Singleton
@Slf4j
public class KafkaStreamService {
    @Inject
    @NotNull
    private ClientConfig clientConfig;

    @Inject
    private StreamDefaultsConfig streamConfig;

    @Inject
    private KafkaConfigService kafkaConfigService;

    @Inject
    private MetricRegistry metricRegistry;

    public KafkaStreamService.Stream of(Class<?> group, Topology topology) {
        return this.of(group, topology, new Properties());
    }

    public KafkaStreamService.Stream of(Class<?> group, Topology topology, Properties properties) {
        properties.putAll(clientConfig.getProperties());

        if (this.streamConfig.getProperties() != null) {
            properties.putAll(streamConfig.getProperties());
        }

        properties.put(CommonClientConfigs.CLIENT_ID_CONFIG, kafkaConfigService.getConsumerGroupName(group));
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, kafkaConfigService.getConsumerGroupName(group));

        return new Stream(topology, properties, metricRegistry);
    }

    public static class Stream extends KafkaStreams {
        private final KafkaStreamsMetrics metrics;

        private Stream(Topology topology, Properties props, MetricRegistry meterRegistry) {
            super(topology, props);

            metrics = new KafkaStreamsMetrics(this);
            meterRegistry.bind(metrics);
        }

        public synchronized void start(final KafkaStreams.StateListener listener) throws IllegalStateException, StreamsException {
            this.setUncaughtExceptionHandler((thread, e) -> {
                log.error("Uncaught exception in Kafka Stream " + thread.getName() + ", closing !", e);
                System.exit(1);
            });

            this.setGlobalStateRestoreListener(new StateRestoreLoggerListeners());

            this.setStateListener((newState, oldState) -> {
                if (log.isTraceEnabled()) {
                    log.trace("Switching stream state from {} to {}", oldState, newState);
                }

                if (listener != null) {
                    listener.onChange(newState, oldState);
                }
            });

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                this.close(Duration.ofSeconds(10));
            }));

            super.start();
        }

        @Override
        public synchronized void start() throws IllegalStateException, StreamsException {
            this.start(null);
        }

        @Override
        public void close() {
            metrics.close();
            super.close();
        }

        @Override
        public boolean close(Duration timeout) {
            metrics.close();
            return super.close(timeout);
        }
    }

    public static class StateRestoreLoggerListeners implements StateRestoreListener {
        @Override
        public void onRestoreStart(TopicPartition topicPartition, String storeName, long startingOffset, long endingOffset) {
            if (log.isDebugEnabled()) {
                log.debug(
                    "Starting restore topic '{}', partition '{}', store '{}' from {} to {}",
                    topicPartition.topic(),
                    topicPartition.partition(),
                    storeName,
                    startingOffset,
                    endingOffset
                );
            }
        }

        @Override
        public void onBatchRestored(TopicPartition topicPartition, String storeName, long batchEndOffset, long numRestored) {
            if (log.isTraceEnabled()) {
                log.trace(
                    "Restore done for topic '{}', partition '{}', store '{}' at offset {} with {} records",
                    topicPartition.topic(),
                    topicPartition.partition(),
                    storeName,
                    batchEndOffset,
                    numRestored
                );
            }
        }

        @Override
        public void onRestoreEnd(TopicPartition topicPartition, String storeName, long totalRestored) {
            if (log.isDebugEnabled()) {
                log.debug(
                    "Restore ended for topic '{}', partition '{}', store '{}'",
                    topicPartition.topic(),
                    topicPartition.partition(),
                    storeName
                );
            }
        }
    }
}
