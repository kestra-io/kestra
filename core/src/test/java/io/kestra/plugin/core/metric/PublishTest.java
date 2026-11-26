package io.kestra.plugin.core.metric;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.metrics.CounterMetric;
import io.kestra.core.models.tasks.metrics.GaugeMetric;
import io.kestra.core.models.tasks.metrics.TimerMetric;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
public class PublishTest {
    @Inject
    private TestRunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        var publish = Publish.builder()
            .id(Publish.class.getSimpleName())
            .type(Publish.class.getName())
            .metrics(
                Property.ofValue(
                    List.of(
                        CounterMetric.builder()
                            .value(Property.ofValue(1.0))
                            .name(Property.ofValue("counter"))
                            .build(),
                        TimerMetric.builder()
                            .value(Property.ofValue(Duration.parse("PT5H")))
                            .name(Property.ofValue("timer"))
                            .build(),
                        GaugeMetric.builder()
                            .value(Property.ofValue(1.0))
                            .name(Property.ofValue("gauge"))
                            .build()
                    )
                )
            )
            .build();

        final RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, publish, Map.of("inputs", Map.of("test", "counter")));

        publish.run(runContext);

        assertThat(runContext.metrics().size()).isEqualTo(3);
    }

    @Test
    void shouldIgnoreNullTagValuesWhenPublishingMetric() throws Exception {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("env", "test");
        tags.put("ignored", null);

        var publish = Publish.builder()
            .id(Publish.class.getSimpleName() + "NullTags")
            .type(Publish.class.getName())
            .metrics(
                Property.ofValue(
                    List.of(
                        CounterMetric.builder()
                            .value(Property.ofValue(1.0))
                            .name(Property.ofValue("counter"))
                            .tags(Property.ofValue(tags))
                            .build()
                    )
                )
            )
            .build();

        final RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, publish, Map.of());

        publish.run(runContext);

        assertThat(runContext.metrics()).hasSize(1);
        assertThat(runContext.metrics().getFirst().getTags())
            .containsEntry("env", "test")
            .doesNotContainKey("ignored");
    }

}
