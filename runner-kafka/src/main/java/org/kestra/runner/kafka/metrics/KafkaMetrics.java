package org.kestra.runner.kafka.metrics;


import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;

import java.util.*;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

import static java.util.Collections.emptyList;

/**
 * Waiting the release
 *
 * @link https://github.com/micrometer-metrics/micrometer/pull/1835
 */
class KafkaMetrics implements MeterBinder {
    static final String METRIC_NAME_PREFIX = "kafka.";
    static final String METRIC_GROUP_APP_INFO = "app-info";
    static final String METRIC_GROUP_METRICS_COUNT = "kafka-metrics-count";
    static final String VERSION_METRIC_NAME = "version";
    static final String START_TIME_METRIC_NAME = "start-time-ms";

    private final Supplier<Map<MetricName, ? extends Metric>> metricsSupplier;
    private final Iterable<Tag> extraTags;

    /**
     * Keeps track of current set of metrics.
     */
    private volatile Set<MetricName> currentMeters = new HashSet<>();

    private String kafkaVersion = "unknown";

    KafkaMetrics(Supplier<Map<MetricName, ? extends Metric>> metricsSupplier) {
        this(metricsSupplier, emptyList());
    }

    KafkaMetrics(Supplier<Map<MetricName, ? extends Metric>> metricsSupplier, Iterable<Tag> extraTags) {
        this.metricsSupplier = metricsSupplier;
        this.extraTags = extraTags;
    }

    @Override public void bindTo(MeterRegistry registry) {
        Map<MetricName, ? extends Metric> metrics = metricsSupplier.get();
        // Collect static metrics and tags
        Metric startTime = null;
        for (Map.Entry<MetricName, ? extends Metric> entry: metrics.entrySet()) {
            MetricName name = entry.getKey();
            if (METRIC_GROUP_APP_INFO.equals(name.group()))
                if (VERSION_METRIC_NAME.equals(name.name()))
                    kafkaVersion = (String) entry.getValue().metricValue();
                else if (START_TIME_METRIC_NAME.equals(name.name()))
                    startTime = entry.getValue();
        }
        if (startTime != null) bindMeter(registry, startTime, meterName(startTime), meterTags(startTime));
        // Collect dynamic metrics
        checkAndBindMetrics(registry);
    }

    /**
     * Gather metrics from Kafka metrics API and register Meters.
     * <p>
     * As this is a one-off execution when binding a Kafka client, Meters include a call to this
     * validation to double-check new metrics when returning values. This should only add the cost of
     * comparing meters last returned from the Kafka client.
     */
    void checkAndBindMetrics(MeterRegistry registry) {
        Map<MetricName, ? extends Metric> metrics = metricsSupplier.get();
        if (!currentMeters.equals(metrics.keySet())) {
            synchronized (this) { //Enforce only happens once when metrics change
                if (!currentMeters.equals(metrics.keySet())) {
                    currentMeters = new HashSet<>(metrics.keySet());
                    metrics.forEach((name, metric) -> {
                        //Filter out metrics from groups that includes metadata
                        if (METRIC_GROUP_APP_INFO.equals(name.group())) return;
                        if (METRIC_GROUP_METRICS_COUNT.equals(name.group())) return;
                        String meterName = meterName(metric);
                        List<Tag> meterTags = meterTags(metric);
                        //Kafka has metrics with lower number of tags (e.g. with/without topic or partition tag)
                        //Remove meters with lower number of tags
                        boolean hasLessTags = false;
                        for (Meter other : registry.find(meterName).meters()) {
                            List<Tag> tags = other.getId().getTags();
                            if (tags.size() < meterTags.size()) registry.remove(other);
                                // Check if already exists
                            else if (tags.size() == meterTags.size())
                                if (tags.equals(meterTags)) return;
                                else break;
                            else hasLessTags = true;
                        }
                        if (hasLessTags) return;
                        //Filter out non-numeric values
                        if (!(metric.metricValue() instanceof Number)) return;
                        bindMeter(registry, metric, meterName, meterTags);
                    });
                }
            }
        }
    }

    private void bindMeter(MeterRegistry registry, Metric metric, String name, Iterable<Tag> tags) {
        if (name.endsWith("total") || name.endsWith("count")) registerCounter(registry, metric, name, tags);
        else if (name.endsWith("min") || name.endsWith("max") || name.endsWith("avg") || name.endsWith("rate"))
            registerGauge(registry, metric, name, tags);
        else registerGauge(registry, metric, name, tags);
    }

    private void registerGauge(MeterRegistry registry, Metric metric, String name, Iterable<Tag> tags) {
        Gauge.builder(name, metric, toMetricValue(registry))
            .tags(tags)
            .description(metric.metricName().description())
            .register(registry);
    }

    private void registerCounter(MeterRegistry registry, Metric metric, String name, Iterable<Tag> tags) {
        FunctionCounter.builder(name, metric, toMetricValue(registry))
            .tags(tags)
            .description(metric.metricName().description())
            .register(registry);
    }

    private ToDoubleFunction<Metric> toMetricValue(MeterRegistry registry) {
        return metric -> {
            //Double-check if new metrics are registered
            checkAndBindMetrics(registry);
            return ((Number) metric.metricValue()).doubleValue();
        };
    }

    private List<Tag> meterTags(Metric metric) {
        List<Tag> tags = new ArrayList<>();
        metric.metricName().tags().forEach((key, value) -> tags.add(Tag.of(key, value)));
        tags.add(Tag.of("kafka-version", kafkaVersion));
        extraTags.forEach(tags::add);
        return tags;
    }

    private String meterName(Metric metric) {
        String name = METRIC_NAME_PREFIX + metric.metricName().group() + "." + metric.metricName().name();
        return name.replaceAll("-metrics", "").replaceAll("-", ".");
    }
}
