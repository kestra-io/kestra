package io.kestra.core.models.triggers.multipleflows;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.TimeWindow;
import io.kestra.core.models.triggers.TimeWindow.Type;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest(transactional = false)
public abstract class AbstractMultipleConditionStateStoreTest {
    private static final String NAMESPACE = "io.kestra.unit";

    @Inject
    private MultipleConditionStateStore multipleConditionStateStore;

    @Test
    void allDefault() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, TimeWindow.builder().build());

        MultipleConditionWindow window = multipleConditionStateStore.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());

        assertThat(window.getStart().toLocalTime()).isEqualTo(LocalTime.parse("00:00:00"));
        assertThat(window.getStart().toLocalDate()).isEqualTo(ZonedDateTime.now().toLocalDate());

        assertThat(window.getEnd().toLocalTime()).isEqualTo(LocalTime.parse("23:59:59.999"));
        assertThat(window.getEnd().toLocalDate()).isEqualTo(ZonedDateTime.now().toLocalDate());
    }

    @Test
    void daily() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, TimeWindow.builder().window(Duration.ofDays(1)).windowAdvance(Duration.ofSeconds(0)).build());

        MultipleConditionWindow window = multipleConditionStateStore.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());

        assertThat(window.getStart().toLocalTime()).isEqualTo(LocalTime.parse("00:00:00"));
        assertThat(window.getStart().toLocalDate()).isEqualTo(ZonedDateTime.now().toLocalDate());

        assertThat(window.getEnd().toLocalTime()).isEqualTo(LocalTime.parse("23:59:59.999"));
        assertThat(window.getEnd().toLocalDate()).isEqualTo(ZonedDateTime.now().toLocalDate());
    }

    @Test
    void dailyAdvance() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, TimeWindow.builder().window(Duration.ofDays(1)).windowAdvance(Duration.ofHours(4).negated()).build());

        MultipleConditionWindow window = multipleConditionStateStore.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());

        assertThat(window.getStart().toLocalTime()).isEqualTo(LocalTime.parse("20:00:00"));
        assertThat(window.getStart().toLocalDate()).isEqualTo(ZonedDateTime.now().minusDays(1).toLocalDate());

        assertThat(window.getEnd().toLocalTime()).isEqualTo(LocalTime.parse("19:59:59.999"));
        assertThat(window.getEnd().toLocalDate()).isEqualTo(ZonedDateTime.now().toLocalDate());
    }

    @Test
    void hourly() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, TimeWindow.builder().window(Duration.ofHours(1)).windowAdvance(Duration.ofHours(4).negated()).build());

        MultipleConditionWindow window = multipleConditionStateStore.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());

        assertThat(window.getStart().toLocalTime().getHour()).isEqualTo(ZonedDateTime.now().minusHours(4).getHour());
        assertThat(window.getStart().toLocalDate()).isEqualTo(ZonedDateTime.now().minusHours(4).toLocalDate());

        assertThat(window.getEnd().toLocalTime().getHour()).isEqualTo(ZonedDateTime.now().minusHours(4).getHour());
        assertThat(window.getEnd().toLocalTime().getMinute()).isEqualTo(59);
        assertThat(window.getEnd().toLocalDate()).isEqualTo(ZonedDateTime.now().minusHours(4).toLocalDate());
    }

    @Test
    void minutely() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, TimeWindow.builder().window(Duration.ofMinutes(15)).windowAdvance(Duration.ofMinutes(5).negated()).build());

        MultipleConditionWindow window = multipleConditionStateStore.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());
        assertThat(window.getStart().getMinute()).isIn(Arrays.asList(10, 25, 40, 55));
        assertThat(window.getEnd().getMinute()).isIn(Arrays.asList(9, 24, 39, 54));
    }

    @Test
    void expiration() throws Exception {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, TimeWindow.builder().window(Duration.ofSeconds(2)).windowAdvance(Duration.ofMinutes(0).negated()).build());

        MultipleConditionWindow window = multipleConditionStateStore.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());
        multipleConditionStateStore.save(Collections.singletonList(window.with(ImmutableMap.of("a", true))));
        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());
        window = multipleConditionStateStore.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getResults().get("a")).isTrue();

        Thread.sleep(2005);

        MultipleConditionWindow next = multipleConditionStateStore.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(next.getStart().format(DateTimeFormatter.ISO_DATE_TIME)).isNotEqualTo(window.getStart().format(DateTimeFormatter.ISO_DATE_TIME));
        assertThat(next.getResults().containsKey("a")).isFalse();
    }

    @Test
    void expired() throws Exception {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, TimeWindow.builder().window(Duration.ofSeconds(2)).windowAdvance(Duration.ofMinutes(0).negated()).build());

        MultipleConditionWindow window = multipleConditionStateStore.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());
        multipleConditionStateStore.save(Collections.singletonList(window.with(ImmutableMap.of("a", true))));
        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());
        window = multipleConditionStateStore.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getResults().get("a")).isTrue();

        List<MultipleConditionWindow> expired = multipleConditionStateStore.expired(tenant);
        assertThat(expired.size()).isZero();

        Thread.sleep(2005);

        expired = multipleConditionStateStore.expired(tenant);
        assertThat(expired.size()).isEqualTo(1);
    }

    @Test
    void dailyTimeDeadline() throws Exception {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, TimeWindow.builder().type(Type.DAILY_TIME_DEADLINE).deadline(LocalTime.now().plusSeconds(2)).build());

        MultipleConditionWindow window = multipleConditionStateStore.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());
        multipleConditionStateStore.save(Collections.singletonList(window.with(ImmutableMap.of("a", true))));
        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());
        window = multipleConditionStateStore.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getResults().get("a")).isTrue();

        List<MultipleConditionWindow> expired = multipleConditionStateStore.expired(tenant);
        assertThat(expired.size()).isZero();

        Thread.sleep(2005);

        expired = multipleConditionStateStore.expired(tenant);
        assertThat(expired.size()).isEqualTo(1);
    }

    @Test
    void dailyTimeDeadline_Expired() throws Exception {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, TimeWindow.builder().type(Type.DAILY_TIME_DEADLINE).deadline(LocalTime.now().minusSeconds(1)).build());

        MultipleConditionWindow window = multipleConditionStateStore.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());
        multipleConditionStateStore.save(Collections.singletonList(window.with(ImmutableMap.of("a", true))));
        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());
        window = multipleConditionStateStore.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getResults()).isEmpty();

        List<MultipleConditionWindow> expired = multipleConditionStateStore.expired(tenant);
        assertThat(expired.size()).isEqualTo(1);
    }

    @Test
    void dailyTimeWindow() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        LocalTime startTime = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);
        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, TimeWindow.builder().type(Type.DAILY_TIME_WINDOW).startTime(startTime).endTime(startTime.plusMinutes(5)).build());

        MultipleConditionWindow window = multipleConditionStateStore.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());
        multipleConditionStateStore.save(Collections.singletonList(window.with(ImmutableMap.of("a", true))));
        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());
        window = multipleConditionStateStore.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getResults().get("a")).isTrue();

        List<MultipleConditionWindow> expired = multipleConditionStateStore.expired(tenant);
        assertThat(expired.size()).isZero();
    }

    @Test
    void slidingWindow() throws Exception {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, TimeWindow.builder().type(Type.SLIDING_WINDOW).window(Duration.ofHours(1)).build());

        MultipleConditionWindow window = multipleConditionStateStore.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());
        multipleConditionStateStore.save(Collections.singletonList(window.with(ImmutableMap.of("a", true))));
        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());
        window = multipleConditionStateStore.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getResults().get("a")).isTrue();

        List<MultipleConditionWindow> expired = multipleConditionStateStore.expired(tenant);
        assertThat(expired.size()).isZero();
    }

    private static Pair<Flow, MultipleCondition> mockFlow(String tenantId, TimeWindow sla) {
        var preconditions = io.kestra.plugin.core.trigger.Flow.Preconditions.builder()
            .id("condition-multiple-%s".formatted(tenantId))
            .flows(
                List.of(
                    io.kestra.plugin.core.trigger.Flow.UpstreamFlow.builder()
                        .flowId("flow-a")
                        .namespace(NAMESPACE)
                        .build(),
                    io.kestra.plugin.core.trigger.Flow.UpstreamFlow.builder()
                        .flowId("flow-b")
                        .namespace(NAMESPACE)
                        .build()
                )
            )
            .timeWindow(sla)
            .build();

        Flow flow = Flow.builder()
            .namespace(NAMESPACE)
            .id("multiple-flow")
            .tenantId(tenantId)
            .revision(1)
            .triggers(
                Collections.singletonList(
                    io.kestra.plugin.core.trigger.Flow.builder()
                        .id("trigger-flow")
                        .preconditions(preconditions)
                        .build()
                )
            )
            .build();

        return Pair.of(flow, preconditions);
    }
}