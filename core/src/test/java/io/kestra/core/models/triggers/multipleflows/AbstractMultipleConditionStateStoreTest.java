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
import io.kestra.core.models.triggers.Window;
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

        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, Window.builder().build());

        MultipleConditionWindow window = multipleConditionStateStore.create(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());

        assertThat(window.getStart().toLocalTime()).isEqualTo(LocalTime.parse("00:00:00"));
        assertThat(window.getStart().toLocalDate()).isEqualTo(ZonedDateTime.now().toLocalDate());

        assertThat(window.getEnd().toLocalTime()).isEqualTo(LocalTime.parse("23:59:59.999"));
        assertThat(window.getEnd().toLocalDate()).isEqualTo(ZonedDateTime.now().toLocalDate());
    }

    @Test
    void daily() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, Window.builder().every(Duration.ofDays(1)).offset(Duration.ofSeconds(0)).build());

        MultipleConditionWindow window = multipleConditionStateStore.create(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());

        assertThat(window.getStart().toLocalTime()).isEqualTo(LocalTime.parse("00:00:00"));
        assertThat(window.getStart().toLocalDate()).isEqualTo(ZonedDateTime.now().toLocalDate());

        assertThat(window.getEnd().toLocalTime()).isEqualTo(LocalTime.parse("23:59:59.999"));
        assertThat(window.getEnd().toLocalDate()).isEqualTo(ZonedDateTime.now().toLocalDate());
    }

    @Test
    void dailyAdvance() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, Window.builder().every(Duration.ofDays(1)).offset(Duration.ofHours(4).negated()).build());

        MultipleConditionWindow window = multipleConditionStateStore.create(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());

        assertThat(window.getStart().toLocalTime()).isEqualTo(LocalTime.parse("20:00:00"));
        assertThat(window.getStart().toLocalDate()).isEqualTo(ZonedDateTime.now().minusDays(1).toLocalDate());

        assertThat(window.getEnd().toLocalTime()).isEqualTo(LocalTime.parse("19:59:59.999"));
        assertThat(window.getEnd().toLocalDate()).isEqualTo(ZonedDateTime.now().toLocalDate());
    }

    @Test
    void hourly() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, Window.builder().every(Duration.ofHours(1)).offset(Duration.ofHours(4).negated()).build());

        MultipleConditionWindow window = multipleConditionStateStore.create(pair.getKey(), pair.getRight(), Collections.emptyMap());

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

        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, Window.builder().every(Duration.ofMinutes(15)).offset(Duration.ofMinutes(5).negated()).build());

        MultipleConditionWindow window = multipleConditionStateStore.create(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());
        assertThat(window.getStart().getMinute()).isIn(Arrays.asList(10, 25, 40, 55));
        assertThat(window.getEnd().getMinute()).isIn(Arrays.asList(9, 24, 39, 54));
    }

    @Test
    void expiration() throws Exception {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, Window.builder().every(Duration.ofSeconds(2)).offset(Duration.ofMinutes(0).negated()).build());

        MultipleConditionWindow window = multipleConditionStateStore.create(pair.getKey(), pair.getRight(), Collections.emptyMap());
        multipleConditionStateStore.save(window.with(ImmutableMap.of("a", true)));
        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());
        window = multipleConditionStateStore.get(pair.getKey(), pair.getRight().getId()).orElseThrow();

        assertThat(window.getResults().get("a")).isTrue();

        Thread.sleep(2005);

        MultipleConditionWindow next = multipleConditionStateStore.create(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(next.getStart().format(DateTimeFormatter.ISO_DATE_TIME)).isNotEqualTo(window.getStart().format(DateTimeFormatter.ISO_DATE_TIME));
        assertThat(next.getResults().containsKey("a")).isFalse();
    }

    @Test
    void expired() throws Exception {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, Window.builder().every(Duration.ofSeconds(2)).offset(Duration.ofMinutes(0).negated()).build());

        MultipleConditionWindow window = multipleConditionStateStore.create(pair.getKey(), pair.getRight(), Collections.emptyMap());
        multipleConditionStateStore.save(window.with(ImmutableMap.of("a", true)));
        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());
        window = multipleConditionStateStore.get(pair.getKey(), pair.getRight().getId()).orElseThrow();

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

        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, Window.builder().deadline(LocalTime.now().plusSeconds(2)).build());

        MultipleConditionWindow window = multipleConditionStateStore.create(pair.getKey(), pair.getRight(), Collections.emptyMap());
        multipleConditionStateStore.save(window.with(ImmutableMap.of("a", true)));
        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());
        window = multipleConditionStateStore.get(pair.getKey(), pair.getRight().getId()).orElseThrow();

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

        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, Window.builder().deadline(LocalTime.now().minusSeconds(1)).build());

        MultipleConditionWindow window = multipleConditionStateStore.create(pair.getKey(), pair.getRight(), Collections.emptyMap());
        multipleConditionStateStore.save(window.with(ImmutableMap.of("a", true)));
        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());
        window = multipleConditionStateStore.create(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getResults()).isEmpty();

        List<MultipleConditionWindow> expired = multipleConditionStateStore.expired(tenant);
        assertThat(expired.size()).isEqualTo(1);
    }

    @Test
    void dailyTimeWindow() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        LocalTime startTime = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);
        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, Window.builder().from(startTime).to(startTime.plusMinutes(5)).build());

        MultipleConditionWindow window = multipleConditionStateStore.create(pair.getKey(), pair.getRight(), Collections.emptyMap());
        multipleConditionStateStore.save(window.with(ImmutableMap.of("a", true)));
        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());
        window = multipleConditionStateStore.get(pair.getKey(), pair.getRight().getId()).orElseThrow();

        assertThat(window.getResults().get("a")).isTrue();

        List<MultipleConditionWindow> expired = multipleConditionStateStore.expired(tenant);
        assertThat(expired.size()).isZero();
    }

    @Test
    void slidingWindow() throws Exception {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, Window.builder().lookback(Duration.ofHours(1)).build());

        MultipleConditionWindow window = multipleConditionStateStore.create(pair.getKey(), pair.getRight(), Collections.emptyMap());
        multipleConditionStateStore.save(window.with(ImmutableMap.of("a", true)));
        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());
        window = multipleConditionStateStore.get(pair.getKey(), pair.getRight().getId()).orElseThrow();

        assertThat(window.getResults().get("a")).isTrue();

        List<MultipleConditionWindow> expired = multipleConditionStateStore.expired(tenant);
        assertThat(expired.size()).isZero();
    }

    @Test
    void allDefault_dependsOn() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlowWithDependsOn(tenant, Window.builder().build());

        MultipleConditionWindow window = multipleConditionStateStore.create(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());

        assertThat(window.getStart().toLocalTime()).isEqualTo(LocalTime.parse("00:00:00"));
        assertThat(window.getStart().toLocalDate()).isEqualTo(ZonedDateTime.now().toLocalDate());

        assertThat(window.getEnd().toLocalTime()).isEqualTo(LocalTime.parse("23:59:59.999"));
        assertThat(window.getEnd().toLocalDate()).isEqualTo(ZonedDateTime.now().toLocalDate());
    }

    @Test
    void daily_dependsOn() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlowWithDependsOn(tenant, Window.builder().every(Duration.ofDays(1)).offset(Duration.ofSeconds(0)).build());

        MultipleConditionWindow window = multipleConditionStateStore.create(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());

        assertThat(window.getStart().toLocalTime()).isEqualTo(LocalTime.parse("00:00:00"));
        assertThat(window.getStart().toLocalDate()).isEqualTo(ZonedDateTime.now().toLocalDate());

        assertThat(window.getEnd().toLocalTime()).isEqualTo(LocalTime.parse("23:59:59.999"));
        assertThat(window.getEnd().toLocalDate()).isEqualTo(ZonedDateTime.now().toLocalDate());
    }

    @Test
    void dailyAdvance_dependsOn() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlowWithDependsOn(tenant, Window.builder().every(Duration.ofDays(1)).offset(Duration.ofHours(4).negated()).build());

        MultipleConditionWindow window = multipleConditionStateStore.create(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());

        assertThat(window.getStart().toLocalTime()).isEqualTo(LocalTime.parse("20:00:00"));
        assertThat(window.getStart().toLocalDate()).isEqualTo(ZonedDateTime.now().minusDays(1).toLocalDate());

        assertThat(window.getEnd().toLocalTime()).isEqualTo(LocalTime.parse("19:59:59.999"));
        assertThat(window.getEnd().toLocalDate()).isEqualTo(ZonedDateTime.now().toLocalDate());
    }

    @Test
    void hourly_dependsOn() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlowWithDependsOn(tenant, Window.builder().every(Duration.ofHours(1)).offset(Duration.ofHours(4).negated()).build());

        MultipleConditionWindow window = multipleConditionStateStore.create(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());

        assertThat(window.getStart().toLocalTime().getHour()).isEqualTo(ZonedDateTime.now().minusHours(4).getHour());
        assertThat(window.getStart().toLocalDate()).isEqualTo(ZonedDateTime.now().minusHours(4).toLocalDate());

        assertThat(window.getEnd().toLocalTime().getHour()).isEqualTo(ZonedDateTime.now().minusHours(4).getHour());
        assertThat(window.getEnd().toLocalTime().getMinute()).isEqualTo(59);
        assertThat(window.getEnd().toLocalDate()).isEqualTo(ZonedDateTime.now().minusHours(4).toLocalDate());
    }

    @Test
    void minutely_dependsOn() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlowWithDependsOn(tenant, Window.builder().every(Duration.ofMinutes(15)).offset(Duration.ofMinutes(5).negated()).build());

        MultipleConditionWindow window = multipleConditionStateStore.create(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());
        assertThat(window.getStart().getMinute()).isIn(Arrays.asList(10, 25, 40, 55));
        assertThat(window.getEnd().getMinute()).isIn(Arrays.asList(9, 24, 39, 54));
    }

    @Test
    void expiration_dependsOn() throws Exception {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlowWithDependsOn(tenant, Window.builder().every(Duration.ofSeconds(2)).offset(Duration.ZERO.negated()).build());

        MultipleConditionWindow window = multipleConditionStateStore.create(pair.getKey(), pair.getRight(), Collections.emptyMap());
        multipleConditionStateStore.save(window.with(ImmutableMap.of("a", true)));
        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());
        window = multipleConditionStateStore.get(pair.getKey(), pair.getRight().getId()).orElseThrow();

        assertThat(window.getResults().get("a")).isTrue();

        Thread.sleep(2005);

        MultipleConditionWindow next = multipleConditionStateStore.create(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(next.getStart().format(DateTimeFormatter.ISO_DATE_TIME)).isNotEqualTo(window.getStart().format(DateTimeFormatter.ISO_DATE_TIME));
        assertThat(next.getResults().containsKey("a")).isFalse();
    }

    @Test
    void expired_dependsOn() throws Exception {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlowWithDependsOn(tenant, Window.builder().every(Duration.ofSeconds(2)).offset(Duration.ZERO.negated()).build());

        MultipleConditionWindow window = multipleConditionStateStore.create(pair.getKey(), pair.getRight(), Collections.emptyMap());
        multipleConditionStateStore.save(window.with(ImmutableMap.of("a", true)));
        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());
        window = multipleConditionStateStore.get(pair.getKey(), pair.getRight().getId()).orElseThrow();

        assertThat(window.getResults().get("a")).isTrue();

        List<MultipleConditionWindow> expired = multipleConditionStateStore.expired(tenant);
        assertThat(expired.size()).isZero();

        Thread.sleep(2005);

        expired = multipleConditionStateStore.expired(tenant);
        assertThat(expired.size()).isEqualTo(1);
    }

    @Test
    void dailyTimeDeadline_dependsOn() throws Exception {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlowWithDependsOn(tenant, Window.builder().deadline(LocalTime.now().plusSeconds(2)).build());

        MultipleConditionWindow window = multipleConditionStateStore.create(pair.getKey(), pair.getRight(), Collections.emptyMap());
        multipleConditionStateStore.save(window.with(ImmutableMap.of("a", true)));
        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());
        window = multipleConditionStateStore.get(pair.getKey(), pair.getRight().getId()).orElseThrow();

        assertThat(window.getResults().get("a")).isTrue();

        List<MultipleConditionWindow> expired = multipleConditionStateStore.expired(tenant);
        assertThat(expired.size()).isZero();

        Thread.sleep(2005);

        expired = multipleConditionStateStore.expired(tenant);
        assertThat(expired.size()).isEqualTo(1);
    }

    @Test
    void dailyTimeDeadline_Expired_dependsOn() throws Exception {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlowWithDependsOn(tenant, Window.builder().deadline(LocalTime.now().minusSeconds(1)).build());

        MultipleConditionWindow window = multipleConditionStateStore.create(pair.getKey(), pair.getRight(), Collections.emptyMap());
        multipleConditionStateStore.save(window.with(ImmutableMap.of("a", true)));
        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());
        window = multipleConditionStateStore.create(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getResults()).isEmpty();

        List<MultipleConditionWindow> expired = multipleConditionStateStore.expired(tenant);
        assertThat(expired.size()).isEqualTo(1);
    }

    @Test
    void dailyTimeWindow_dependsOn() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        LocalTime startTime = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);
        Pair<Flow, MultipleCondition> pair = mockFlowWithDependsOn(tenant, Window.builder().from(startTime).to(startTime.plusMinutes(5)).build());

        MultipleConditionWindow window = multipleConditionStateStore.create(pair.getKey(), pair.getRight(), Collections.emptyMap());
        multipleConditionStateStore.save(window.with(ImmutableMap.of("a", true)));
        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());
        window = multipleConditionStateStore.get(pair.getKey(), pair.getRight().getId()).orElseThrow();

        assertThat(window.getResults().get("a")).isTrue();

        List<MultipleConditionWindow> expired = multipleConditionStateStore.expired(tenant);
        assertThat(expired.size()).isZero();
    }

    @Test
    void slidingWindow_dependsOn() throws Exception {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlowWithDependsOn(tenant, Window.builder().lookback(Duration.ofHours(1)).build());

        MultipleConditionWindow window = multipleConditionStateStore.create(pair.getKey(), pair.getRight(), Collections.emptyMap());
        multipleConditionStateStore.save(window.with(ImmutableMap.of("a", true)));
        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());
        window = multipleConditionStateStore.get(pair.getKey(), pair.getRight().getId()).orElseThrow();

        assertThat(window.getResults().get("a")).isTrue();

        List<MultipleConditionWindow> expired = multipleConditionStateStore.expired(tenant);
        assertThat(expired.size()).isZero();
    }

    private static Pair<Flow, MultipleCondition> mockFlowWithDependsOn(String tenantId, Window window) {
        io.kestra.plugin.core.trigger.Flow flowTrigger = io.kestra.plugin.core.trigger.Flow.builder()
            .id("trigger-flow")
            .dependsOn(
                List.of(
                    io.kestra.plugin.core.trigger.Flow.Dependency.builder()
                        .flowId("flow-a")
                        .namespace(NAMESPACE)
                        .build(),
                    io.kestra.plugin.core.trigger.Flow.Dependency.builder()
                        .flowId("flow-b")
                        .namespace(NAMESPACE)
                        .build()
                )
            )
            .window(window)
            .build();

        Flow flow = Flow.builder()
            .namespace(NAMESPACE)
            .id("multiple-flow")
            .tenantId(tenantId)
            .revision(1)
            .triggers(Collections.singletonList(flowTrigger))
            .build();

        return Pair.of(flow, flowTrigger.dependsOnAsMultipleCondition());
    }

    private static Pair<Flow, MultipleCondition> mockFlow(String tenantId, Window sla) {
        var dependency1 = io.kestra.plugin.core.trigger.Flow.Dependency.builder()
            .flowId("flow-a")
            .namespace(NAMESPACE)
            .build();
        var dependency2 = io.kestra.plugin.core.trigger.Flow.Dependency.builder()
            .flowId("flow-b")
            .namespace(NAMESPACE)
            .build();

        var trigger = io.kestra.plugin.core.trigger.Flow.builder()
            .id("trigger-flow")
            .dependsOn(List.of(dependency1, dependency2))
            .window(sla)
            .build();

        Flow flow = Flow.builder()
            .namespace(NAMESPACE)
            .id("multiple-flow")
            .tenantId(tenantId)
            .revision(1)
            .triggers(Collections.singletonList(trigger))
            .build();

        return Pair.of(flow, trigger.dependsOnAsMultipleCondition());
    }
}