package io.kestra.plugin.core.condition;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.services.ConditionService;
import io.kestra.core.utils.TestsUtils;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class PublicHolidayTest {
    @Inject
    ConditionService conditionService;
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void valid() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        PublicHoliday publicHoliday = PublicHoliday.builder()
            .date(Property.ofValue("2023-07-14"))
            .country(Property.ofValue("FR"))
            .build();
        assertThat(conditionService.isValid(publicHoliday, flow, execution)).isTrue();

        publicHoliday = PublicHoliday.builder()
            .date(Property.ofValue("2023-03-08"))
            .country(Property.ofValue("DE"))
            .subDivision(Property.ofValue("BE"))
            .build();
        assertThat(conditionService.isValid(publicHoliday, flow, execution)).isTrue();
    }

    @Test
    void invalid() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        PublicHoliday publicHoliday = PublicHoliday.builder()
            .date(Property.ofValue("2023-01-02"))
            .country(Property.ofValue("FR"))
            .build();
        assertThat(conditionService.isValid(publicHoliday, flow, execution)).isFalse();

        publicHoliday = PublicHoliday.builder()
            .date(Property.ofValue("2023-03-08"))
            .country(Property.ofValue("DE"))
            .build();
        assertThat(conditionService.isValid(publicHoliday, flow, execution)).isFalse();
    }

    @Test
    void validWithDynamicRender() {
        Flow flow = TestsUtils.mockFlow();

        Map<String, Object> variables = Map.of(
            "trigger", Map.of("date", "2023-07-14")
        );
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());
        PublicHoliday publicHoliday = PublicHoliday.builder()
            .country(Property.ofValue("FR"))
            .build();
       ConditionContext conditionContext= ConditionContext.builder()
            .flow(flow)
            .execution(execution)
            .runContext(runContextFactory.of(flow,execution))
            .variables(variables)
            .build();
        assertThat(conditionService.valid(flow, Collections.singletonList(publicHoliday), conditionContext)).isTrue();
    }
    @Test
    void invalidWithDynamicRender() {
        Flow flow = TestsUtils.mockFlow();

        Map<String, Object> variables = Map.of(
            "trigger", Map.of("date", "2023-01-02")
        );
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());
        PublicHoliday publicHoliday = PublicHoliday.builder()
            .country(Property.ofValue("FR"))
            .build();
        ConditionContext conditionContext= ConditionContext.builder()
            .flow(flow)
            .execution(execution)
            .runContext(runContextFactory.of(flow,execution))
            .variables(variables)
            .build();
        assertThat(conditionService.valid(flow, Collections.singletonList(publicHoliday), conditionContext)).isFalse();
    }
    @Test
    @Disabled("Locale is not deterministic on CI")
    void disabled() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        PublicHoliday publicHoliday = PublicHoliday.builder()
            .date(Property.ofValue("2023-01-01"))
            .build();
        assertThat(conditionService.isValid(publicHoliday, flow, execution)).isTrue();
    }
}