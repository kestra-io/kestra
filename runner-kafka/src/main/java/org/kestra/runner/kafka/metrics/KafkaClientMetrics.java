package org.kestra.runner.kafka.metrics;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;

/**
 * Waiting the release
 *
 * @link https://github.com/micrometer-metrics/micrometer/pull/1835
 */
public class KafkaClientMetrics extends KafkaMetrics implements MeterBinder {

    /**
     * Kafka {@link Producer} metrics binder
     *
     * @param kafkaProducer producer instance to be instrumented
     * @param tags          additional tags
     */
    public KafkaClientMetrics(Producer<?, ?> kafkaProducer, Iterable<Tag> tags) {
        super(kafkaProducer::metrics, tags);
    }

    /**
     * Kafka {@link Producer} metrics binder
     *
     * @param kafkaProducer producer instance to be instrumented
     */
    public KafkaClientMetrics(Producer<?, ?> kafkaProducer) {
        super(kafkaProducer::metrics);
    }

    /**
     * Kafka {@link Consumer} metrics binder
     *
     * @param kafkaConsumer consumer instance to be instrumented
     * @param tags          additional tags
     */
    public KafkaClientMetrics(Consumer<?, ?> kafkaConsumer, Iterable<Tag> tags) {
        super(kafkaConsumer::metrics, tags);
    }

    /**
     * Kafka {@link Consumer} metrics binder
     *
     * @param kafkaConsumer consumer instance to be instrumented
     */
    public KafkaClientMetrics(Consumer<?, ?> kafkaConsumer) {
        super(kafkaConsumer::metrics);
    }

    /**
     * Kafka {@link AdminClient} metrics binder
     *
     * @param adminClient instance to be instrumented
     * @param tags        additional tags
     */
    public KafkaClientMetrics(AdminClient adminClient, Iterable<Tag> tags) {
        super(adminClient::metrics, tags);
    }

    /**
     * Kafka {@link AdminClient} metrics binder
     *
     * @param adminClient instance to be instrumented
     */
    public KafkaClientMetrics(AdminClient adminClient) {
        super(adminClient::metrics);
    }
}
