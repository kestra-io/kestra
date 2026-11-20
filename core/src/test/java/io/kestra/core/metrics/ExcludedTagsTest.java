package io.kestra.core.metrics;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micronaut.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class ExcludedTagsTest {
    private final MetricConfig config = config(Map.of(
        "all", Map.of("excluded-tags", List.of("flow_id", "namespace_id", "label_*")),
        "worker", Map.of("ended", Map.of("count", Map.of("excluded-tags", List.of("state")))),
        "queue", Map.of("lag", Map.of("included-tags", List.of("queue_name")))
    ));

    private static MetricConfig config(Map<String, Object> properties) {
        MetricConfig config = new MetricConfig(null, null, null, Map.of());
        config.useTagRules(MetricTagRules.fromProperties(properties));
        return config;
    }

    private MetricRegistry registry(SimpleMeterRegistry simple) {
        CompositeMeterRegistry composite = new CompositeMeterRegistry();
        composite.config().meterFilter(new ExcludedTagsMeterFilter(config.getTagRules()));
        composite.add(simple);
        return new MetricRegistry(composite, config);
    }

    @Test
    void shouldApplyAllExclusionsAndMetricRules() {
        assertThat(config.isTagExcluded("any", "flow_id")).isTrue();
        assertThat(config.isTagExcluded("any", "label_team")).isTrue();
        assertThat(config.isTagExcluded("any", "state")).isFalse();
        assertThat(config.isTagExcluded("worker.ended.count", "state")).isTrue();
        assertThat(config.isTagExcluded("worker.ended.count", "flow_id")).isFalse();
        assertThat(config.isTagExcluded("queue.lag", "queue_name")).isFalse();
        assertThat(config.isTagExcluded("queue.lag", "worker_queue")).isTrue();
    }

    @Test
    void shouldMergeCountersWhenExcludedTagsDiffer() {
        SimpleMeterRegistry simple = new SimpleMeterRegistry();
        MetricRegistry registry = registry(simple);

        registry.counter("c", "d", "flow_id", "a", "label_team", "x", "state", "OK").increment();
        registry.counter("c", "d", "flow_id", "b", "label_team", "y", "state", "OK").increment();

        assertThat(simple.find("c").counters()).hasSize(1);
        assertThat(simple.find("c").counter().count()).isEqualTo(2D);
        assertThat(simple.find("c").counter().getId().getTags()).extracting(t -> t.getKey()).containsExactly("state");
    }

    @Test
    void shouldSumGaugesWhenExcludedTagsDiffer() {
        SimpleMeterRegistry simple = new SimpleMeterRegistry();
        MetricRegistry registry = registry(simple);

        AtomicInteger first = registry.gauge("g", "d", new AtomicInteger(2), "flow_id", "a", "worker_group", "w");
        AtomicInteger second = registry.gauge("g", "d", new AtomicInteger(3), "flow_id", "b", "worker_group", "w");

        assertThat(simple.find("g").gauges()).hasSize(1);
        assertThat(simple.find("g").gauge().value()).isEqualTo(5D);
        second.addAndGet(1);
        first.addAndGet(1);
        assertThat(simple.find("g").gauge().value()).isEqualTo(7D);
    }

    @Test
    void shouldKeepGaugesSeparateWhenNothingExcluded() {
        SimpleMeterRegistry simple = new SimpleMeterRegistry();
        MetricRegistry registry = registry(simple);

        registry.gauge("g2", "d", new AtomicInteger(2), "worker_group", "a");
        registry.gauge("g2", "d", new AtomicInteger(3), "worker_group", "b");

        assertThat(simple.find("g2").gauges()).hasSize(2);
    }

    @Test
    void shouldKeepOnlyIncludedTagsOfMetric() {
        SimpleMeterRegistry simple = new SimpleMeterRegistry();
        MetricRegistry registry = registry(simple);

        registry.counter("queue.lag", "d", "queue_name", "q", "worker_queue", "w", "flow_id", "f").increment();

        assertThat(simple.find("queue.lag").counter().getId().getTags()).extracting(t -> t.getKey()).containsExactly("queue_name");
    }

    @Test
    void shouldReadRulesFromConfigurationWithDottedMetricNamesAndPrefix() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "kestra.metrics.prefix", "kestra",
            "kestra.metrics.all.excluded-tags", List.of("flow_id"),
            "kestra.metrics.worker.ended.count.included-tags", List.of("state")
        ))) {
            MetricTagRules rules = MetricTagRules.fromEnvironment(context.getEnvironment());

            assertThat(rules.isExcluded("kestra.any", "flow_id")).isTrue();
            assertThat(rules.isExcluded("kestra.worker.ended.count", "state")).isFalse();
            assertThat(rules.isExcluded("kestra.worker.ended.count", "namespace_id")).isTrue();
        }
    }
}
