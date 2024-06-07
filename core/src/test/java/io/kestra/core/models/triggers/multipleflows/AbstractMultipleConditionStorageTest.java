package io.kestra.core.models.triggers.multipleflows;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import io.kestra.plugin.core.condition.ExecutionFlowCondition;
import io.kestra.plugin.core.condition.MultipleCondition;
import io.kestra.core.models.flows.Flow;
import org.junitpioneer.jupiter.RetryingTest;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
public abstract class AbstractMultipleConditionStorageTest {
    private static final String NAMESPACE = "io.kestra.unit";

    abstract protected MultipleConditionStorageInterface multipleConditionStorage();

    abstract protected void save(MultipleConditionStorageInterface multipleConditionStorage, Flow flow, List<MultipleConditionWindow> multipleConditionWindows);

    @Test
    void daily() {
        MultipleConditionStorageInterface multipleConditionStorage = multipleConditionStorage();

        Pair<Flow, MultipleCondition> pair = mockFlow(Duration.ofDays(1), Duration.ofSeconds(0));

        MultipleConditionWindow window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight());

        assertThat(window.getFlowId(), is(pair.getLeft().getId()));

        assertThat(window.getStart().toLocalTime(), is(LocalTime.parse("00:00:00")));
        assertThat(window.getStart().toLocalDate(), is(ZonedDateTime.now().toLocalDate()));

        assertThat(window.getEnd().toLocalTime(), is(LocalTime.parse("23:59:59.999")));
        assertThat(window.getEnd().toLocalDate(), is(ZonedDateTime.now().toLocalDate()));
    }

    @Test
    void dailyAdvance() {
        MultipleConditionStorageInterface multipleConditionStorage = multipleConditionStorage();

        Pair<Flow, MultipleCondition> pair = mockFlow(Duration.ofDays(1), Duration.ofHours(4).negated());

        MultipleConditionWindow window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight());

        assertThat(window.getFlowId(), is(pair.getLeft().getId()));

        assertThat(window.getStart().toLocalTime(), is(LocalTime.parse("20:00:00")));
        assertThat(window.getStart().toLocalDate(), is(ZonedDateTime.now().minusDays(1).toLocalDate()));

        assertThat(window.getEnd().toLocalTime(), is(LocalTime.parse("19:59:59.999")));
        assertThat(window.getEnd().toLocalDate(), is(ZonedDateTime.now().toLocalDate()));
    }

    @Test
    void hourly() {
        MultipleConditionStorageInterface multipleConditionStorage = multipleConditionStorage();

        Pair<Flow, MultipleCondition> pair = mockFlow(Duration.ofHours(1), Duration.ofHours(4).negated());

        MultipleConditionWindow window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight());

        assertThat(window.getFlowId(), is(pair.getLeft().getId()));

        assertThat(window.getStart().toLocalTime().getHour(), is(ZonedDateTime.now().minusHours(4).getHour()));
        assertThat(window.getStart().toLocalDate(), is(ZonedDateTime.now().minusHours(4).toLocalDate()));

        assertThat(window.getEnd().toLocalTime().getHour(), is(ZonedDateTime.now().minusHours(4).getHour()));
        assertThat(window.getEnd().toLocalTime().getMinute(), is(59));
        assertThat(window.getEnd().toLocalDate(), is(ZonedDateTime.now().minusHours(4).toLocalDate()));
    }

    @Test
    void minutely() {
        MultipleConditionStorageInterface multipleConditionStorage = multipleConditionStorage();

        Pair<Flow, MultipleCondition> pair = mockFlow(Duration.ofMinutes(15), Duration.ofMinutes(5).negated());

        MultipleConditionWindow window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight());

        assertThat(window.getFlowId(), is(pair.getLeft().getId()));
        assertThat(window.getStart().getMinute(), is(in(Arrays.asList(10, 25, 40, 55))));
        assertThat(window.getEnd().getMinute(), is(in(Arrays.asList(9, 24, 39, 54))));
    }

    @RetryingTest(5)
    void expiration() throws Exception {
        MultipleConditionStorageInterface multipleConditionStorage = multipleConditionStorage();

        Pair<Flow, MultipleCondition> pair = mockFlow(Duration.ofSeconds(1), Duration.ofMinutes(0).negated());

        MultipleConditionWindow window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight());
        this.save(multipleConditionStorage, pair.getLeft(), Collections.singletonList(window.with(ImmutableMap.of("a", true))));
        assertThat(window.getFlowId(), is(pair.getLeft().getId()));
        window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight());

        assertThat(window.getResults().get("a"), is(true));

        Thread.sleep(1005);

        MultipleConditionWindow next = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight());

        assertThat(
            next.getStart().format(DateTimeFormatter.ISO_DATE_TIME),
            is(not(window.getStart().format(DateTimeFormatter.ISO_DATE_TIME)))
        );
        assertThat(next.getResults().containsKey("a"), is(false));
    }

    @Test
    void expired() throws Exception {
        MultipleConditionStorageInterface multipleConditionStorage = multipleConditionStorage();

        Pair<Flow, MultipleCondition> pair = mockFlow(Duration.ofSeconds(2), Duration.ofMinutes(0).negated());

        MultipleConditionWindow window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight());
        this.save(multipleConditionStorage, pair.getLeft(), Collections.singletonList(window.with(ImmutableMap.of("a", true))));
        assertThat(window.getFlowId(), is(pair.getLeft().getId()));
        window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight());

        assertThat(window.getResults().get("a"), is(true));

        List<MultipleConditionWindow> expired = multipleConditionStorage.expired(null);
        assertThat(expired.size(), is(0));

        Thread.sleep(2005);

        expired = multipleConditionStorage.expired(null);
        assertThat(expired.size(), is(1));
    }

    private static Pair<Flow, MultipleCondition> mockFlow(Duration window, Duration advance) {
        MultipleCondition multipleCondition = MultipleCondition.builder()
            .id("condition-multiple")
            .window(window)
            .windowAdvance(advance)
            .conditions(ImmutableMap.of(
                "flow-a", ExecutionFlowCondition.builder()
                    .flowId("flow-a")
                    .namespace(NAMESPACE)
                    .build(),
                "flow-b", ExecutionFlowCondition.builder()
                    .flowId("flow-b")
                    .namespace(NAMESPACE)
                    .build()
            ))
            .build();

        Flow flow = Flow.builder()
            .namespace(NAMESPACE)
            .id("multiple-flow")
            .revision(1)
            .triggers(Collections.singletonList(io.kestra.plugin.core.trigger.Flow.builder()
                .id("trigger-flow")
                .conditions(Collections.singletonList(multipleCondition))
                .build()))
            .build();

        return Pair.of(flow, multipleCondition);
    }
}