package io.kestra.core.models.triggers.multipleflows;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import io.kestra.plugin.core.condition.ExecutionFlow;
import io.kestra.plugin.core.condition.MultipleCondition;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.TimeWindow;
import io.kestra.core.models.triggers.TimeWindow.Type;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest(transactional = false)
public abstract class AbstractMultipleConditionStorageTest {
    private static final String NAMESPACE = "io.kestra.unit";

    abstract protected MultipleConditionStorageInterface multipleConditionStorage();

    abstract protected void save(MultipleConditionStorageInterface multipleConditionStorage, Flow flow, List<MultipleConditionWindow> multipleConditionWindows);

    @Test
    void allDefault() {
        MultipleConditionStorageInterface multipleConditionStorage = multipleConditionStorage();
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, TimeWindow.builder().build());

        MultipleConditionWindow window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());

        assertThat(window.getStart().toLocalTime()).isEqualTo(LocalTime.parse("00:00:00"));
        assertThat(window.getStart().toLocalDate()).isEqualTo(ZonedDateTime.now().toLocalDate());

        assertThat(window.getEnd().toLocalTime()).isEqualTo(LocalTime.parse("23:59:59.999"));
        assertThat(window.getEnd().toLocalDate()).isEqualTo(ZonedDateTime.now().toLocalDate());
    }

    @Test
    void daily() {
        MultipleConditionStorageInterface multipleConditionStorage = multipleConditionStorage();
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, TimeWindow.builder().window(Duration.ofDays(1)).windowAdvance(Duration.ofSeconds(0)).build());

        MultipleConditionWindow window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());

        assertThat(window.getStart().toLocalTime()).isEqualTo(LocalTime.parse("00:00:00"));
        assertThat(window.getStart().toLocalDate()).isEqualTo(ZonedDateTime.now().toLocalDate());

        assertThat(window.getEnd().toLocalTime()).isEqualTo(LocalTime.parse("23:59:59.999"));
        assertThat(window.getEnd().toLocalDate()).isEqualTo(ZonedDateTime.now().toLocalDate());
    }

    @Test
    void dailyAdvance() {
        MultipleConditionStorageInterface multipleConditionStorage = multipleConditionStorage();
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, TimeWindow.builder().window(Duration.ofDays(1)).windowAdvance(Duration.ofHours(4).negated()).build());

        MultipleConditionWindow window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());

        assertThat(window.getStart().toLocalTime()).isEqualTo(LocalTime.parse("20:00:00"));
        assertThat(window.getStart().toLocalDate()).isEqualTo(ZonedDateTime.now().minusDays(1).toLocalDate());

        assertThat(window.getEnd().toLocalTime()).isEqualTo(LocalTime.parse("19:59:59.999"));
        assertThat(window.getEnd().toLocalDate()).isEqualTo(ZonedDateTime.now().toLocalDate());
    }

    @Test
    void hourly() {
        MultipleConditionStorageInterface multipleConditionStorage = multipleConditionStorage();
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, TimeWindow.builder().window(Duration.ofHours(1)).windowAdvance(Duration.ofHours(4).negated()).build());

        MultipleConditionWindow window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());

        assertThat(window.getStart().toLocalTime().getHour()).isEqualTo(ZonedDateTime.now().minusHours(4).getHour());
        assertThat(window.getStart().toLocalDate()).isEqualTo(ZonedDateTime.now().minusHours(4).toLocalDate());

        assertThat(window.getEnd().toLocalTime().getHour()).isEqualTo(ZonedDateTime.now().minusHours(4).getHour());
        assertThat(window.getEnd().toLocalTime().getMinute()).isEqualTo(59);
        assertThat(window.getEnd().toLocalDate()).isEqualTo(ZonedDateTime.now().minusHours(4).toLocalDate());
    }

    @Test
    void minutely() {
        MultipleConditionStorageInterface multipleConditionStorage = multipleConditionStorage();
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, TimeWindow.builder().window(Duration.ofMinutes(15)).windowAdvance(Duration.ofMinutes(5).negated()).build());

        MultipleConditionWindow window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());
        assertThat(window.getStart().getMinute()).isIn(Arrays.asList(10, 25, 40, 55));
        assertThat(window.getEnd().getMinute()).isIn(Arrays.asList(9, 24, 39, 54));
    }

    @Test
    void expiration() throws Exception {
        MultipleConditionStorageInterface multipleConditionStorage = multipleConditionStorage();
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, TimeWindow.builder().window(Duration.ofSeconds(2)).windowAdvance(Duration.ofMinutes(0).negated()).build());

        MultipleConditionWindow window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());
        this.save(multipleConditionStorage, pair.getLeft(), Collections.singletonList(window.with(ImmutableMap.of("a", true))));
        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());
        window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getResults().get("a")).isTrue();

        Thread.sleep(2005);

        MultipleConditionWindow next = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(next.getStart().format(DateTimeFormatter.ISO_DATE_TIME)).isNotEqualTo(window.getStart().format(DateTimeFormatter.ISO_DATE_TIME));
        assertThat(next.getResults().containsKey("a")).isFalse();
    }

    @Test
    void expired() throws Exception {
        MultipleConditionStorageInterface multipleConditionStorage = multipleConditionStorage();
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, TimeWindow.builder().window(Duration.ofSeconds(2)).windowAdvance(Duration.ofMinutes(0).negated()).build());

        MultipleConditionWindow window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());
        this.save(multipleConditionStorage, pair.getLeft(), Collections.singletonList(window.with(ImmutableMap.of("a", true))));
        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());
        window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getResults().get("a")).isTrue();

        List<MultipleConditionWindow> expired = multipleConditionStorage.expired(tenant);
        assertThat(expired.size()).isZero();

        Thread.sleep(2005);

        expired = multipleConditionStorage.expired(tenant);
        assertThat(expired.size()).isEqualTo(1);
    }

    @Test
    void dailyTimeDeadline() throws Exception {
        MultipleConditionStorageInterface multipleConditionStorage = multipleConditionStorage();
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, TimeWindow.builder().type(Type.DAILY_TIME_DEADLINE).deadline(LocalTime.now().plusSeconds(2)).build());

        MultipleConditionWindow window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());
        this.save(multipleConditionStorage, pair.getLeft(), Collections.singletonList(window.with(ImmutableMap.of("a", true))));
        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());
        window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getResults().get("a")).isTrue();

        List<MultipleConditionWindow> expired = multipleConditionStorage.expired(tenant);
        assertThat(expired.size()).isZero();

        Thread.sleep(2005);

        expired = multipleConditionStorage.expired(tenant);
        assertThat(expired.size()).isEqualTo(1);
    }

    @Test
    void dailyTimeDeadline_Expired() throws Exception {
        MultipleConditionStorageInterface multipleConditionStorage = multipleConditionStorage();
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, TimeWindow.builder().type(Type.DAILY_TIME_DEADLINE).deadline(LocalTime.now().minusSeconds(1)).build());

        MultipleConditionWindow window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());
        this.save(multipleConditionStorage, pair.getLeft(), Collections.singletonList(window.with(ImmutableMap.of("a", true))));
        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());
        window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getResults()).isEmpty();

        List<MultipleConditionWindow> expired = multipleConditionStorage.expired(tenant);
        assertThat(expired.size()).isEqualTo(1);
    }

    @Test
    void dailyTimeWindow() {
        MultipleConditionStorageInterface multipleConditionStorage = multipleConditionStorage();
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        LocalTime startTime = LocalTime.now().truncatedTo(ChronoUnit.MINUTES);
        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, TimeWindow.builder().type(Type.DAILY_TIME_WINDOW).startTime(startTime).endTime(startTime.plusMinutes(5)).build());

        MultipleConditionWindow window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());
        this.save(multipleConditionStorage, pair.getLeft(), Collections.singletonList(window.with(ImmutableMap.of("a", true))));
        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());
        window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getResults().get("a")).isTrue();

        List<MultipleConditionWindow> expired = multipleConditionStorage.expired(tenant);
        assertThat(expired.size()).isZero();
    }

    @Test
    void slidingWindow() throws Exception {
        MultipleConditionStorageInterface multipleConditionStorage = multipleConditionStorage();
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        Pair<Flow, MultipleCondition> pair = mockFlow(tenant, TimeWindow.builder().type(Type.SLIDING_WINDOW).window(Duration.ofHours(1)).build());

        MultipleConditionWindow window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());
        this.save(multipleConditionStorage, pair.getLeft(), Collections.singletonList(window.with(ImmutableMap.of("a", true))));
        assertThat(window.getFlowId()).isEqualTo(pair.getLeft().getId());
        window = multipleConditionStorage.getOrCreate(pair.getKey(), pair.getRight(), Collections.emptyMap());

        assertThat(window.getResults().get("a")).isTrue();

        List<MultipleConditionWindow> expired = multipleConditionStorage.expired(tenant);
        assertThat(expired.size()).isZero();
    }

    private static Pair<Flow, MultipleCondition> mockFlow(String tenantId, TimeWindow sla) {
        var multipleCondition = MultipleCondition.builder()
            .id("condition-multiple-%s".formatted(tenantId))
            .conditions(ImmutableMap.of(
                "flow-a", ExecutionFlow.builder()
                    .flowId(Property.ofValue("flow-a"))
                    .namespace(Property.ofValue(NAMESPACE))
                    .build(),
                "flow-b", ExecutionFlow.builder()
                    .flowId(Property.ofValue("flow-b"))
                    .namespace(Property.ofValue(NAMESPACE))
                    .build()
            ))
            .timeWindow(sla)
            .build();

        Flow flow = Flow.builder()
            .namespace(NAMESPACE)
            .id("multiple-flow")
            .tenantId(tenantId)
            .revision(1)
            .triggers(Collections.singletonList(io.kestra.plugin.core.trigger.Flow.builder()
                .id("trigger-flow")
                .conditions(Collections.singletonList(multipleCondition))
                .build()))
            .build();

        return Pair.of(flow, multipleCondition);
    }
}