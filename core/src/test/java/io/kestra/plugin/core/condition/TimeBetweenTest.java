package io.kestra.plugin.core.condition;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetTime;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class TimeBetweenTest {
    
    @Inject
    RunContextFactory runContextFactory;

    static Stream<Arguments> source() {
        return Stream.of(
            // Normal ranges
            Arguments.of("2024-02-21T16:19:12.00+02:00", null, OffsetTime.parse("16:19:11.000000+02:00").toString(), true),
            Arguments.of("2024-02-21T16:19:12.00+02:00", null, OffsetTime.parse("17:19:12.000000+02:00").toString(), false),
            Arguments.of("2024-02-21T16:19:12.00+02:00", OffsetTime.parse("16:20:12.000000+02:00"), OffsetTime.parse("16:18:12.000000+02:00"), true),
            Arguments.of("2024-02-21T16:19:12.00+02:00", OffsetTime.parse("16:20:12.000000+02:00"), null, true),
            Arguments.of("2024-02-21T16:19:12.00+02:00", OffsetTime.parse("16:18:12.000000+02:00"), null, false),
            // Cross-midnight ranges
            Arguments.of("2025-11-18T23:30:00+02:00", OffsetTime.parse("02:00:00+02:00"), OffsetTime.parse("22:00:00+02:00"), true),
            Arguments.of("2025-11-18T04:00:00+02:00", OffsetTime.parse("02:00:00+02:00"), OffsetTime.parse("22:00:00+02:00"), false)
        );
    }

    @ParameterizedTest
    @MethodSource("source")
    void valid(String date, OffsetTime before, OffsetTime after, boolean result) throws InternalException {
        TimeBetween build = TimeBetween.builder()
            .before(Property.ofValue(before))
            .after(Property.ofValue(after))
            .build();
        
        ConditionContext conditionContext = ConditionContext.builder()
            .variables(Map.of("trigger", Map.of("date", date)))
            .runContext(runContextFactory.of())
            .build();
        
        // WHEN
        boolean test = build.test(conditionContext);
        
        // THEN
        assertThat(test).isEqualTo(result);
    }
}
