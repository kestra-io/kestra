package io.kestra.runner.kafka.services;

import io.kestra.runner.kafka.*;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.kafka.KafkaStreamsMetrics;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.ApplicationEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
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

import java.io.File;
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
    private ApplicationEventPublisher<KafkaStreamEndpoint.Event> eventPublisher;

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

        if (properties.containsKey(StreamsConfig.STATE_DIR_CONFIG)) {
            File stateDir = new File((String) properties.get(StreamsConfig.STATE_DIR_CONFIG));

            if (!stateDir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                stateDir.mkdirs();
            }
        }

        Stream stream = new Stream(topology, properties, metricsEnabled ? metricRegistry : null, logger);
        eventPublisher.publishEvent(new KafkaStreamEndpoint.Event(clientId.getName(), stream));

        return stream;
    }

    public static class Stream extends KafkaStreams {
        private final Logger logger;

        private final MetricRegistry meterRegistry;

        private final String[] tags;

        private KafkaStreamsMetrics metrics;

        private boolean hasStarted = false;

        private Stream(Topology topology, Properties props, MetricRegistry meterRegistry, Logger logger) {
            super(topology, props);
            this.meterRegistry = meterRegistry;

            tags = new String[]{
                "client_class_id",
                (String) props.get(CommonClientConfigs.CLIENT_ID_CONFIG)
            };

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

            if (this.logger.isTraceEnabled()) {
                this.logger.trace(topology.describe().toString());
            }
        }

        public synchronized void start(final KafkaStreams.StateListener listener) throws IllegalStateException, StreamsException {
            this.setUncaughtExceptionHandler(e -> {
                this.logger.error("Uncaught exception in Kafka Stream, closing !", e);
                return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.SHUTDOWN_APPLICATION;
            });

            this.setGlobalStateRestoreListener(new StateRestoreLoggerListeners(logger));

            this.setStateListener((newState, oldState) -> {
                meterRegistry.gauge(
                    MetricRegistry.STREAMS_STATE_COUNT,
                    0,
                    ArrayUtils.addAll(tags, "state", oldState.name())
                );

                meterRegistry.gauge(
                    MetricRegistry.STREAMS_STATE_COUNT,
                    1,
                    ArrayUtils.addAll(tags, "state", newState.name())
                );

                if (newState == State.RUNNING) {
                    this.hasStarted = true;
                }

                if (
                    (newState == State.REBALANCING && this.hasStarted) ||
                    newState == State.NOT_RUNNING ||
                    newState == State.PENDING_SHUTDOWN
                ) {
                    this.logger.warn("Switching stream state from {} to {}", oldState, newState);
                } else if (
                    newState == State.PENDING_ERROR ||
                    newState == State.ERROR
                ) {
                    this.logger.error("Switching stream state from {} to {}", oldState, newState);
                } else {
                    logger.info("Switching stream state from {} to {}", oldState, newState);
                }

                if (newState == State.ERROR) {
                    logger.warn("Shutdown now due to ERROR state");
                    System.exit(-1);
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
