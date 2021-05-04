package io.kestra.core.models.conditions.types;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.services.ConditionService;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.stream.Stream;
import javax.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
class WeekendConditionTest {
    @Inject
    ConditionService conditionService;

    static Stream<Arguments> source() {
        return Stream.of(
            Arguments.of(LocalDate.parse("2013-09-08").toString(), true),
            Arguments.of(LocalDate.parse("2013-09-07").toString(), true),
            Arguments.of(LocalDate.parse("2013-09-07").toString(), true),
            Arguments.of("{{ dateFormat \"2013-09-08T15:19:12.000000+02:00\" \"iso_local_date\" }}", true)
        );
    }

    @ParameterizedTest
    @MethodSource("source")
    void valid(String date, boolean result) {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        WeekendCondition build = WeekendCondition.builder()
            .date(date)
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        assertThat(test, is(result));
    }
}