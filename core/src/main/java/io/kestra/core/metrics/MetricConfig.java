package io.kestra.core.metrics;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.kestra.core.server.ServiceType;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.env.Environment;
import jakarta.inject.Inject;
import io.micronaut.core.convert.format.MapFormat;
import lombok.*;

@Getter
@ConfigurationProperties("kestra.metrics")
public class MetricConfig {
    String prefix;

    @MapFormat(transformation = MapFormat.MapTransformation.FLAT)
    Map<String, String> tags;

    /**
     * {@link io.kestra.core.models.Label} keys included to metrics.
     */
    List<String> labels;

    Map<ServiceType, Set<String>> sharedServiceInstanceMetrics = Map.of();

    /**
     * Rules dropping tags from metrics, see {@link MetricTagRules}.
     */
    MetricTagRules tagRules = MetricTagRules.NONE;

    @Inject
    public MetricConfig() {
    }

    public MetricConfig(String prefix, Map<String, String> tags, List<String> labels, Map<ServiceType, Set<String>> sharedServiceInstanceMetrics) {
        this.prefix = prefix;
        this.tags = tags;
        this.labels = labels;
        this.sharedServiceInstanceMetrics = sharedServiceInstanceMetrics;
    }

    @Inject
    void loadTagRules(Environment environment) {
        this.tagRules = MetricTagRules.fromEnvironment(environment);
    }

    public void useTagRules(MetricTagRules tagRules) {
        this.tagRules = tagRules;
    }

    public boolean isTagExcluded(String meterName, String tagKey) {
        return tagRules.isExcluded(meterName, tagKey);
    }
}
