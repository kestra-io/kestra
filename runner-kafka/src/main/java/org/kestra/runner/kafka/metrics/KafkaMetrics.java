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
    private final int extraTagsSize;

    /**
     * Keeps track of current set of metrics. When this values change, metrics are bound again.
     */
    private volatile Set<MetricName> currentMeters = new HashSet<>();

    private String kafkaVersion = "unknown";

    KafkaMetrics(Supplier<Map<MetricName, ? extends Metric>> metricsSupplier) {
        this(metricsSupplier, emptyList());
    }

    KafkaMetrics(Supplier<Map<MetricName, ? extends Metric>> metricsSupplier, Iterable<Tag> extraTags) {
        this.metricsSupplier = metricsSupplier;
        this.extraTags = extraTags;
        int i = 0;
        for (Tag ignored : extraTags) i++;
        this.extraTagsSize = i + 1; // 1 = kafka version tag
    }

    @Override public void bindTo(MeterRegistry registry) {
        Map<MetricName, ? extends Metric> metrics = metricsSupplier.get();
        // Collect static metrics and tags
        Metric startTimeMetric = null;
        for (Map.Entry<MetricName, ? extends Metric> entry: metrics.entrySet()) {
            MetricName name = entry.getKey();
            if (METRIC_GROUP_APP_INFO.equals(name.group())) {
                if (VERSION_METRIC_NAME.equals(name.name()))
                    kafkaVersion = (String) entry.getValue().metricValue();
                else if (START_TIME_METRIC_NAME.equals(name.name()))
                    startTimeMetric = entry.getValue();
            }
        }
        if (startTimeMetric != null) bindMeter(registry, startTimeMetric);
        // Collect dynamic metrics
        checkAndBindMetrics(registry);
    }

    /**
     * Gather metrics from Kafka metrics API and register Meters.
     * <p>
     * As this is a one-off execution when binding a Kafka client, Meters include a call to this
     * validation to double-check new metrics when returning values. This should only add the cost of
     * validating meters registered counter when no new meters are present.
     */
    void checkAndBindMetrics(MeterRegistry registry) {
        Map<MetricName, ? extends Metric> metrics = metricsSupplier.get();
        if (!currentMeters.equals(metrics.keySet())) {
            synchronized (this) { //Enforce only happens once when metrics change
                if (!currentMeters.equals(metrics.keySet())) {
                    //Register meters
                    currentMeters = new HashSet<>(metrics.keySet());
                    metrics.forEach((name, metric) -> {
                        //Filter out metrics from groups that includes metadata
                        if (METRIC_GROUP_APP_INFO.equals(name.group())) return;
                        if (METRIC_GROUP_METRICS_COUNT.equals(name.group())) return;
                        //Kafka has metrics with lower number of tags (e.g. with/without topic or partition tag)
                        //Remove meters with lower number of tags
                        boolean hasLessTags = false;
                        Collection<Meter> meters = registry.find(metricName(metric)).meters();
                        for (Meter meter : meters) {
                            if (meter.getId().getTags().size() < (metricTags(metric).size() + extraTagsSize))
                                registry.remove(meter);
                                // Check if already exists
                            else if (meter.getId().getTags().equals(metricTags(metric))) return;
                            else hasLessTags = true;
                        }
                        if (hasLessTags) return;
                        //Filter out non-numeric values
                        if (!isNumber(metric)) return;
                        bindMeter(registry, metric);
                    });
                }
            }
        }
    }

    private boolean isNumber(Metric metric) {
        return metric.metricValue() instanceof Double
            || metric.metricValue() instanceof Float
            || metric.metricValue() instanceof Integer
            || metric.metricValue() instanceof Long;
    }

    private void bindMeter(MeterRegistry registry, Metric metric) {
        String name = metricName(metric);
        if (name.endsWith("total") || name.endsWith("count"))
            registerCounter(registry, metric, name, extraTags);
        else if (name.endsWith("min") || name.endsWith("max") || name.endsWith("avg") || name.endsWith("rate"))
            registerGauge(registry, metric, name, extraTags);
        else registerGauge(registry, metric, name, extraTags);
    }

    private void registerGauge(MeterRegistry registry, Metric metric, String metricName, Iterable<Tag> extraTags) {
        Gauge.builder(metricName, metric, toMetricValue(registry))
            .tags(metricTags(metric))
            .tags(extraTags)
            .description(metric.metricName().description())
            .register(registry);
    }

    private void registerCounter(MeterRegistry registry, Metric metric, String metricName, Iterable<Tag> extraTags) {
        FunctionCounter.builder(metricName, metric, toMetricValue(registry))
            .tags(metricTags(metric))
            .tags(extraTags)
            .description(metric.metricName().description())
            .register(registry);
    }

    private ToDoubleFunction<Metric> toMetricValue(MeterRegistry registry) {
        return metric -> {
            //Double-check if new metrics are registered; if not (common scenario)
            //it only adds metrics count validation
            checkAndBindMetrics(registry);
            if (metric.metricValue() instanceof Double) return (double) metric.metricValue();
            else if (metric.metricValue() instanceof Integer) return ((Integer) metric.metricValue()).doubleValue();
            else if (metric.metricValue() instanceof Long) return ((Long) metric.metricValue()).doubleValue();
            else return ((Float) metric.metricValue()).doubleValue();
        };
    }

    private List<Tag> metricTags(Metric metric) {
        List<Tag> tags = new ArrayList<>();
        tags.add(Tag.of("kafka-version", kafkaVersion));
        metric.metricName().tags().forEach((key, value) -> tags.add(Tag.of(key, value)));
        return tags;
    }

    private String metricName(Metric metric) {
        String name = METRIC_NAME_PREFIX + metric.metricName().group() + "." + metric.metricName().name();
        return name.replaceAll("-metrics", "").replaceAll("-", ".");
    }
}
