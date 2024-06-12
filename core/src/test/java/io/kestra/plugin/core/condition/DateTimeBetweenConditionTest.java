package io.kestra.plugin.core.condition;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.services.ConditionService;
import io.kestra.core.utils.TestsUtils;
import io.kestra.core.junit.annotations.KestraTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.ZonedDateTime;
import java.util.stream.Stream;
import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
class DateTimeBetweenConditionTest {
    @Inject
    ConditionService conditionService;

    static Stream<Arguments> source() {
        return Stream.of(
            Arguments.of(ZonedDateTime.now().toString(), null, ZonedDateTime.parse("2013-09-08T16:19:12.000000+02:00"), true),
            Arguments.of(ZonedDateTime.parse("2013-09-08T16:19:12.000000+02:00").toString(), null, ZonedDateTime.now(), false),
            Arguments.of(ZonedDateTime.parse("2013-09-08T16:19:12.000000+02:00").toString(), ZonedDateTime.parse("2013-09-08T16:20:12.000000+02:00"), ZonedDateTime.parse("2013-09-08T16:18:12.000000+02:00"), true),
            Arguments.of(ZonedDateTime.parse("2013-09-08T16:19:12.000000+02:00").toString(), ZonedDateTime.parse("2013-09-08T16:20:12.000000+02:00"), null, true),
            Arguments.of("{{ now() }}", ZonedDateTime.now().plusHours(1), ZonedDateTime.now().plusHours(-1), true),
            Arguments.of("{{ now() }}", ZonedDateTime.now().plusHours(-1), null, false)
        );
    }

    @ParameterizedTest
    @MethodSource("source")
    void valid(String date, ZonedDateTime before, ZonedDateTime after, boolean result) {
        Flow flow = TestsUtils.mockFlow();
        Execution execution = TestsUtils.mockExecution(flow, ImmutableMap.of());

        DateTimeBetweenCondition build = DateTimeBetweenCondition.builder()
            .date(date)
            .before(before)
            .after(after)
            .build();

        boolean test = conditionService.isValid(build, flow, execution);

        assertThat(test, is(result));
    }
}
