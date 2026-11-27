package io.kestra.core.metrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micronaut.context.env.Environment;
import jakarta.inject.Singleton;

/**
 * Drops the tags removed by the {@code kestra.metrics} tag rules from every meter, so meters that only differ by
 * those tags are reported as a single one.
 */
@Singleton
public class ExcludedTagsMeterFilter implements MeterFilter {
    private final MetricTagRules rules;

    public ExcludedTagsMeterFilter(Environment environment) {
        this(MetricTagRules.fromEnvironment(environment));
    }

    ExcludedTagsMeterFilter(MetricTagRules rules) {
        this.rules = rules;
    }

    @Override
    public Meter.Id map(Meter.Id id) {
        if (id.getTags().stream().noneMatch(tag -> rules.isExcluded(id.getName(), tag.getKey()))) {
            return id;
        }

        return id.replaceTags(Tags.of(id.getTags().stream().filter(tag -> !rules.isExcluded(id.getName(), tag.getKey())).toArray(Tag[]::new)));
    }
}
