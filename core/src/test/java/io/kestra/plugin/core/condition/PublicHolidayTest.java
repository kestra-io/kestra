package io.kestra.plugin.core.condition;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.property.Property;
import io.kestra.core.services.ConditionService;
import io.kestra.core.utils.TestsUtils;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class PublicHolidayTest {
    @Inject
    ConditionService conditionService;

    @Test
    void valid() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        PublicHoliday publicHoliday = PublicHoliday.builder()
            .date(Property.of("2023-01-01"))
            .build();
        assertThat(conditionService.isValid(publicHoliday, flow, execution)).isTrue();

        publicHoliday = PublicHoliday.builder()
            .date(Property.of("2023-07-14"))
            .country(Property.of("FR"))
            .build();
        assertThat(conditionService.isValid(publicHoliday, flow, execution)).isTrue();

        publicHoliday = PublicHoliday.builder()
            .date(Property.of("2023-03-08"))
            .country(Property.of("DE"))
            .subDivision(Property.of("BE"))
            .build();
        assertThat(conditionService.isValid(publicHoliday, flow, execution)).isTrue();
    }

    @Test
    void invalid() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        PublicHoliday publicHoliday = PublicHoliday.builder()
            .date(Property.of("2023-01-02"))
            .country(Property.of("FR"))
            .build();
        assertThat(conditionService.isValid(publicHoliday, flow, execution)).isFalse();

        publicHoliday = PublicHoliday.builder()
            .date(Property.of("2023-03-08"))
            .country(Property.of("DE"))
            .build();
        assertThat(conditionService.isValid(publicHoliday, flow, execution)).isFalse();
    }
}