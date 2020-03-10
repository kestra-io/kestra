package org.kestra.runner.kafka.metrics;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.apache.kafka.streams.KafkaStreams;

/**
 * Waiting the release
 *
 * @link https://github.com/micrometer-metrics/micrometer/pull/1835
 */
public class KafkaStreamsMetrics extends KafkaMetrics implements MeterBinder  {
    /**
     * {@link KafkaStreams} metrics binder
     *
     * @param kafkaStreams instance to be instrumented
     * @param tags         additional tags
     */
    public KafkaStreamsMetrics(KafkaStreams kafkaStreams, Iterable<Tag> tags) {
        super(kafkaStreams::metrics, tags);
    }

    /**
     * {@link KafkaStreams} metrics binder
     *
     * @param kafkaStreams instance to be instrumented
     */
    public KafkaStreamsMetrics(KafkaStreams kafkaStreams) {
        super(kafkaStreams::metrics);
    }
}
