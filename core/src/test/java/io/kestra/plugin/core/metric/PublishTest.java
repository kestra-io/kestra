package io.kestra.plugin.core.metric;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.metrics.CounterMetric;
import io.kestra.core.models.tasks.metrics.TimerMetric;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
public class PublishTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        var publish = Publish.builder()
            .id(Publish.class.getSimpleName())
            .type(Publish.class.getName())
            .metrics(
                Property.of(List.of(
                    CounterMetric.builder()
                        .value(Property.of(1.0))
                        .name(Property.of("counter"))
                        .build(),
                    TimerMetric.builder()
                        .value(Property.of(Duration.parse("PT5H")))
                        .name(Property.of("timer"))
                        .build()
                ))
            )
            .build();

        final RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, publish, Map.of("inputs", Map.of("test", "counter")));

        publish.run(runContext);

        assertThat(runContext.metrics().size(), is(2));
    }

}
