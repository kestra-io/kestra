package org.kestra.task.serdes.avro.converter;

import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.kestra.task.serdes.avro.AvroConverterTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

public class LogicalTimeTest {
    static Stream<Arguments> source() {
        return Stream.of(
            Arguments.of("12:13", LocalTime.parse("12:13+01:00", DateTimeFormatter.ISO_TIME)),
            Arguments.of("12:13:11", LocalTime.parse("12:13:11+01:00", DateTimeFormatter.ISO_TIME)),
            Arguments.of("12:13:11.123000", LocalTime.parse("12:13:11.123000", DateTimeFormatter.ISO_TIME)),
            Arguments.of("12:13:11+01:00", LocalTime.parse("12:13:11+01:00", DateTimeFormatter.ISO_TIME)),
            Arguments.of("12:13:11.123000+01:00", LocalTime.parse("12:13:11.123000+01:00", DateTimeFormatter.ISO_TIME))
        );
    }

    @ParameterizedTest
    @MethodSource("source")
    void convert(CharSequence v, LocalTime expected) throws Exception {
        AvroConverterTest.Utils.oneField(v, expected, LogicalTypes.timeMicros().addToSchema(Schema.create(Schema.Type.LONG)));
        AvroConverterTest.Utils.oneField(v, expected, LogicalTypes.timeMillis().addToSchema(Schema.create(Schema.Type.INT)));
    }

    static Stream<Arguments> failedSource() {
        return Stream.of(
            Arguments.of("12:26:2019"),
            Arguments.of("12+0100")
        );
    }

    @ParameterizedTest
    @MethodSource("failedSource")
    void failed(Object v) {
        AvroConverterTest.Utils.oneFieldFailed(v, LogicalTypes.timeMicros().addToSchema(Schema.create(Schema.Type.LONG)));
    }
}
