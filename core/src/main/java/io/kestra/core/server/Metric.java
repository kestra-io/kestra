package io.kestra.core.server;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A serializable representation of a metric.
 *
 * @param name        The name of the metric.
 * @param description A human-readable description to include in the metric. This is optional
 */
public record Metric(
    String name,
    String type,
    String description,
    String baseUnit,
    List<Tag> tags,
    Object value
) {

    /**
     * Static method for constructing a new {@link Metric} from a given {@link Meter}.
     *
     * @param meter the Gauge.
     * @return a new {@link Metric}.
     */
    public static Metric of(final Meter meter) {
        Objects.requireNonNull(meter, "Cannot create Metric from null");

        final AtomicReference<Object> value = new AtomicReference<>();
        meter.use(
            gauge -> value.set(gauge.value()),
            counter -> value.set(counter.count()),
            timer -> value.set(timer.count()),
            distributionSummary -> {},
            longTaskTimer -> {},
            timeGauge -> value.set(timeGauge.value()),
            functionCounter -> value.set(functionCounter.count()),
            functionTimer -> {},
            any -> {}
        );

        return new Metric(
            meter.getId().getName(),
            meter.getId().getType().name(),
            meter.getId().getDescription(),
            meter.getId().getBaseUnit(),
            meter.getId().getTags().stream().map(Tag::of).toList(),
            value.get()
        );
    }

    record Tag(String key, String value) {

        public static Tag of(final io.micrometer.core.instrument.Tag tag) {
            return new Tag(tag.getKey(), tag.getValue());
        }
    }
}
