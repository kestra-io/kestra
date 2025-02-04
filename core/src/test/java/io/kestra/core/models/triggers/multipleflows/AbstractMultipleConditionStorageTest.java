package io.kestra.core.models.triggers.multipleflows;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import io.kestra.plugin.core.condition.ExecutionFlow;
import io.kestra.plugin.core.condition.MultipleCondition;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.TimeWindow;
import io.kestra.core.models.triggers.TimeWindow.Type;
import org.junitpioneer.jupiter.RetryingTest;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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
    void allDefault() {
        MultipleConditionStorageInterface multipleConditionStorage = multipleConditionStorage();

        Pair<Flow, MultipleCondition> pair = mockFlow(TimeWindow.builder().build());

        MultipleConditionWindow window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight());

        assertThat(window.getFlowId(), is(pair.getLeft().getId()));

        assertThat(window.getStart().toLocalTime(), is(LocalTime.parse("00:00:00")));
        assertThat(window.getStart().toLocalDate(), is(ZonedDateTime.now().toLocalDate()));

        assertThat(window.getEnd().toLocalTime(), is(LocalTime.parse("23:59:59.999")));
        assertThat(window.getEnd().toLocalDate(), is(ZonedDateTime.now().toLocalDate()));
    }

    @Test
    void daily() {
        MultipleConditionStorageInterface multipleConditionStorage = multipleConditionStorage();

        Pair<Flow, MultipleCondition> pair = mockFlow(TimeWindow.builder().window(Duration.ofDays(1)).windowAdvance(Duration.ofSeconds(0)).build());

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

        Pair<Flow, MultipleCondition> pair = mockFlow(TimeWindow.builder().window(Duration.ofDays(1)).windowAdvance(Duration.ofHours(4).negated()).build());

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

        Pair<Flow, MultipleCondition> pair = mockFlow(TimeWindow.builder().window(Duration.ofHours(1)).windowAdvance(Duration.ofHours(4).negated()).build());

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

        Pair<Flow, MultipleCondition> pair = mockFlow(TimeWindow.builder().window(Duration.ofMinutes(15)).windowAdvance(Duration.ofMinutes(5).negated()).build());

        MultipleConditionWindow window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight());

        assertThat(window.getFlowId(), is(pair.getLeft().getId()));
        assertThat(window.getStart().getMinute(), is(in(Arrays.asList(10, 25, 40, 55))));
        assertThat(window.getEnd().getMinute(), is(in(Arrays.asList(9, 24, 39, 54))));
    }

    @Test
    void expiration() throws Exception {
        MultipleConditionStorageInterface multipleConditionStorage = multipleConditionStorage();

        Pair<Flow, MultipleCondition> pair = mockFlow(TimeWindow.builder().window(Duration.ofSeconds(2)).windowAdvance(Duration.ofMinutes(0).negated()).build());

        MultipleConditionWindow window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight());
        this.save(multipleConditionStorage, pair.getLeft(), Collections.singletonList(window.with(ImmutableMap.of("a", true))));
        assertThat(window.getFlowId(), is(pair.getLeft().getId()));
        window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight());

        assertThat(window.getResults().get("a"), is(true));

        Thread.sleep(2005);

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

        Pair<Flow, MultipleCondition> pair = mockFlow(TimeWindow.builder().window(Duration.ofSeconds(2)).windowAdvance(Duration.ofMinutes(0).negated()).build());

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

    @Test
    void dailyTimeDeadline() throws Exception {
        MultipleConditionStorageInterface multipleConditionStorage = multipleConditionStorage();

        Pair<Flow, MultipleCondition> pair = mockFlow(TimeWindow.builder().type(Type.DAILY_TIME_DEADLINE).deadline(LocalTime.now().plusSeconds(2)).build());

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

    @Test
    void dailyTimeDeadline_Expired() throws Exception {
        MultipleConditionStorageInterface multipleConditionStorage = multipleConditionStorage();

        Pair<Flow, MultipleCondition> pair = mockFlow(TimeWindow.builder().type(Type.DAILY_TIME_DEADLINE).deadline(LocalTime.now().minusSeconds(1)).build());

        MultipleConditionWindow window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight());
        this.save(multipleConditionStorage, pair.getLeft(), Collections.singletonList(window.with(ImmutableMap.of("a", true))));
        assertThat(window.getFlowId(), is(pair.getLeft().getId()));
        window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight());

        assertThat(window.getResults(), anEmptyMap());

        List<MultipleConditionWindow> expired = multipleConditionStorage.expired(null);
        assertThat(expired.size(), is(1));
    }

    @Test
    void dailyTimeWindow() throws Exception {
        MultipleConditionStorageInterface multipleConditionStorage = multipleConditionStorage();

        LocalTime startTime = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);
        Pair<Flow, MultipleCondition> pair = mockFlow(TimeWindow.builder().type(Type.DAILY_TIME_WINDOW).startTime(startTime).endTime(startTime.plusMinutes(5)).build());

        MultipleConditionWindow window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight());
        this.save(multipleConditionStorage, pair.getLeft(), Collections.singletonList(window.with(ImmutableMap.of("a", true))));
        assertThat(window.getFlowId(), is(pair.getLeft().getId()));
        window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight());

        assertThat(window.getResults().get("a"), is(true));

        List<MultipleConditionWindow> expired = multipleConditionStorage.expired(null);
        assertThat(expired.size(), is(0));
    }

    @Test
    void slidingWindow() throws Exception {
        MultipleConditionStorageInterface multipleConditionStorage = multipleConditionStorage();

        Pair<Flow, MultipleCondition> pair = mockFlow(TimeWindow.builder().type(Type.SLIDING_WINDOW).window(Duration.ofHours(1)).build());

        MultipleConditionWindow window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight());
        this.save(multipleConditionStorage, pair.getLeft(), Collections.singletonList(window.with(ImmutableMap.of("a", true))));
        assertThat(window.getFlowId(), is(pair.getLeft().getId()));
        window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight());

        assertThat(window.getResults().get("a"), is(true));

        List<MultipleConditionWindow> expired = multipleConditionStorage.expired(null);
        assertThat(expired.size(), is(0));
    }

    private static Pair<Flow, MultipleCondition> mockFlow(TimeWindow sla) {
        var multipleCondition = MultipleCondition.builder()
            .id("condition-multiple")
            .conditions(ImmutableMap.of(
                "flow-a", ExecutionFlow.builder()
                    .flowId("flow-a")
                    .namespace(NAMESPACE)
                    .build(),
                "flow-b", ExecutionFlow.builder()
                    .flowId("flow-b")
                    .namespace(NAMESPACE)
                    .build()
            ))
            .timeWindow(sla)
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