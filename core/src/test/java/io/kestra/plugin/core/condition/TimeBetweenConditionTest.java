package io.kestra.plugin.core.condition;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.services.ConditionService;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
class TimeBetweenConditionTest {
    @Inject
    ConditionService conditionService;

    static Stream<Arguments> source() {
        return Stream.of(
            Arguments.of(ZonedDateTime.parse("2024-02-21T16:19:12.00+02:00").toString(), null, OffsetTime.parse("16:19:11.000000+02:00").toString(), true),
            Arguments.of(ZonedDateTime.parse("2024-02-21T16:19:12.00+02:00").toString(), null, OffsetTime.parse("17:19:12.000000+02:00").toString(), false),
            Arguments.of(ZonedDateTime.parse("2024-02-21T16:19:12.00+02:00").toString(), OffsetTime.parse("16:20:12.000000+02:00"), OffsetTime.parse("16:18:12.000000+02:00"), true),
            Arguments.of(ZonedDateTime.parse("2024-02-21T16:19:12.00+02:00").toString(), OffsetTime.parse("16:20:12.000000+02:00"), null, true),
            Arguments.of(ZonedDateTime.parse("2024-02-21T16:19:12.00+02:00").toString(), OffsetTime.parse("16:18:12.000000+02:00"), null, false)
        );
    }

    @ParameterizedTest
    @MethodSource("source")
    void valid(String date, OffsetTime before, OffsetTime after, boolean result) {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        TimeBetweenCondition build = TimeBetweenCondition.builder()
            .date(date)
            .before(before)
            .after(after)
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        assertThat(test, is(result));
    }
}
