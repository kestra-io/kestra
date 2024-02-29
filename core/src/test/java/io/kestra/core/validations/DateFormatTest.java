package io.kestra.core.validations;

import io.kestra.core.models.validations.ModelValidator;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.stream.Stream;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
class DateFormatTest {
    @Inject
    private ModelValidator modelValidator;

    @AllArgsConstructor
    @Introspected
    @Getter
    @Builder
    public static class DateFormatCls {
        @DateFormat
        String dateFormat;

        @DateFormat
        String datetimeFormat;

        @DateFormat
        String timeFormat;
    }

    static Stream<Arguments> formatSource() {
        return Stream.of(
            Arguments.of("YYYY","YYYYHH:mm","H:mm", false, 0),
            Arguments.of("YYYYo","YYYYHH:mm","HH:mm", true, 1),
            Arguments.of("YYYYo","YYYYHH:mm","YYYY", true, 1),
            Arguments.of("YYYYo","YYYYHH:mmo","H:mm", true, 2),
            Arguments.of("YYYYo","YYYYHH:mmo","H:mmo", true, 3)

        );
    }

    @ParameterizedTest
    @MethodSource("formatSource")
    void format(String date, String dateTime, String time, Boolean present, int size) {
        var options =  DateFormatCls.builder()
            .dateFormat(date)
            .datetimeFormat(dateTime)
            .timeFormat(time)
            .build();

        Optional<ConstraintViolationException> valid = modelValidator.isValid(options);

        assertThat(valid.isPresent(), is(present));
        valid.ifPresent(e -> assertThat(e.getConstraintViolations().size(), is(size)));
    }
}
