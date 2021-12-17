package io.kestra.runner.kafka.services;

import io.kestra.runner.kafka.ConsumerInterceptor;
import io.kestra.runner.kafka.KafkaDeserializationExceptionHandler;
import io.kestra.runner.kafka.KafkaExecutorProductionExceptionHandler;
import io.kestra.runner.kafka.ProducerInterceptor;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.kafka.KafkaStreamsMetrics;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.errors.StreamsException;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.apache.kafka.streams.processor.StateRestoreListener;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.runner.kafka.configs.ClientConfig;
import io.kestra.runner.kafka.configs.StreamDefaultsConfig;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javax.validation.constraints.NotNull;

@Singleton
@Slf4j
public class KafkaStreamService {
    public static final String APPLICATION_CONTEXT_CONFIG = "application.context";

    @Inject
    @NotNull
    private ClientConfig clientConfig;

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private StreamDefaultsConfig streamConfig;

    @Inject
    private KafkaConfigService kafkaConfigService;

    @Inject
    private MetricRegistry metricRegistry;

    @Value("${kestra.server.metrics.kafka.stream:true}")
    protected Boolean metricsEnabled;

    public KafkaStreamService.Stream of(Class<?> clientId, Class<?> groupId, Topology topology) {
        return this.of(clientId, groupId, topology, new Properties());
    }

    public KafkaStreamService.Stream of(Class<?> clientId, Class<?> groupId, Topology topology, Logger logger) {
        return this.of(clientId, groupId, topology, new Properties(), logger);
    }

    public KafkaStreamService.Stream of(Class<?> clientId, Class<?> groupId, Topology topology, Properties properties) {
        return this.of(clientId, groupId, topology, properties, null);
    }

    public KafkaStreamService.Stream of(Class<?> clientId, Class<?> groupId, Topology topology, Properties properties, Logger logger) {
        properties.putAll(clientConfig.getProperties());

        if (this.streamConfig.getProperties() != null) {
            properties.putAll(streamConfig.getProperties());
        }

        properties.put(CommonClientConfigs.CLIENT_ID_CONFIG, clientId.getName());
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, kafkaConfigService.getConsumerGroupName(groupId));

        // hack, we send application context in order to use on exception handler
        properties.put(StreamsConfig.DEFAULT_PRODUCTION_EXCEPTION_HANDLER_CLASS_CONFIG, KafkaExecutorProductionExceptionHandler.class);
        properties.put(APPLICATION_CONTEXT_CONFIG, applicationContext);

        properties.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG, KafkaDeserializationExceptionHandler.class);

        // interceptor
        if (clientConfig.getLoggers() != null) {
            properties.put(
                StreamsConfig.PRODUCER_PREFIX + ProducerConfig.INTERCEPTOR_CLASSES_CONFIG,
                ProducerInterceptor.class.getName()
            );

            properties.put(
                StreamsConfig.MAIN_CONSUMER_PREFIX + ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG,
                ConsumerInterceptor.class.getName()
            );
        }

        return new Stream(topology, properties, metricsEnabled ? metricRegistry : null, logger);
    }

    public static class Stream extends KafkaStreams {
        private final Logger logger;
        private KafkaStreamsMetrics metrics;

        private Stream(Topology topology, Properties props, MetricRegistry meterRegistry, Logger logger) {
            super(topology, props);

            if (meterRegistry != null) {
                metrics = new KafkaStreamsMetrics(
                    this,
                    List.of(
                        Tag.of("client_type", "stream"),
                        Tag.of("client_class_id", (String) props.get(CommonClientConfigs.CLIENT_ID_CONFIG))
                    )
                );
                meterRegistry.bind(metrics);
            }

            this.logger = logger != null ? logger : log;
        }

        public synchronized void start(final KafkaStreams.StateListener listener) throws IllegalStateException, StreamsException {
            this.setUncaughtExceptionHandler(e -> {
                log.error("Uncaught exception in Kafka Stream, closing !", e);
                return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.SHUTDOWN_APPLICATION;
            });

            this.setGlobalStateRestoreListener(new StateRestoreLoggerListeners(logger));

            this.setStateListener((newState, oldState) -> {
                if (logger.isInfoEnabled()) {
                    logger.info("Switching stream state from {} to {}", oldState, newState);
                }

                if (listener != null) {
                    listener.onChange(newState, oldState);
                }
            });

            super.start();
        }

        @Override
        public synchronized void start() throws IllegalStateException, StreamsException {
            this.start(null);
        }

        @Override
        public void close() {
            if (metrics != null) {
                metrics.close();
            }

            super.close();
        }

        @Override
        public boolean close(Duration timeout) {
            if (metrics != null) {
                metrics.close();
            }

            return super.close(timeout);
        }
    }

    public static class StateRestoreLoggerListeners implements StateRestoreListener {
        private final Logger logger;

        public StateRestoreLoggerListeners(Logger logger) {
            this.logger = logger;
        }

        @Override
        public void onRestoreStart(TopicPartition topicPartition, String storeName, long startingOffset, long endingOffset) {
            if (logger.isDebugEnabled()) {
                logger.debug(
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
            if (logger.isTraceEnabled()) {
                logger.trace(
                    "Restore batch for topic '{}', partition '{}', store '{}' at offset {} with {} records",
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
            if (logger.isDebugEnabled()) {
                logger.debug(
                    "Restore ended for topic '{}', partition '{}', store '{}'",
                    topicPartition.topic(),
                    topicPartition.partition(),
                    storeName
                );
            }
        }
    }
}
