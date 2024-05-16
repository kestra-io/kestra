package io.kestra.plugin.core.condition;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.services.ConditionService;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
class PublicHolidayConditionTest {
    @Inject
    ConditionService conditionService;

    @Test
    void valid() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        PublicHolidayCondition publicHoliday = PublicHolidayCondition.builder()
            .date("2023-01-01")
            .build();
        assertThat(conditionService.isValid(publicHoliday, flow, execution), is(true));

        publicHoliday = PublicHolidayCondition.builder()
            .date("2023-07-14")
            .country("FR")
            .build();
        assertThat(conditionService.isValid(publicHoliday, flow, execution), is(true));

        publicHoliday = PublicHolidayCondition.builder()
            .date("2023-03-08")
            .country("DE")
            .subDivision("BE")
            .build();
        assertThat(conditionService.isValid(publicHoliday, flow, execution), is(true));
    }

    @Test
    void invalid() {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        PublicHolidayCondition publicHoliday = PublicHolidayCondition.builder()
            .date("2023-01-02")
            .country("FR")
            .build();
        assertThat(conditionService.isValid(publicHoliday, flow, execution), is(false));

        publicHoliday = PublicHolidayCondition.builder()
            .date("2023-03-08")
            .country("DE")
            .build();
        assertThat(conditionService.isValid(publicHoliday, flow, execution), is(false));
    }
}