package org.kestra.task.serdes.avro.converter;

import org.apache.avro.Schema;
import org.kestra.task.serdes.avro.AvroConverterTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

public class PrimitiveNullTest {
    static Stream<Arguments> source() {
        return Stream.of(
            Arguments.of("NULL", null),
            Arguments.of("n/a", null),
            Arguments.of("N/A", null),
            Arguments.of("", null)
        );
    }

    @ParameterizedTest
    @MethodSource("source")
    void convert(Object v, Object expected) throws Exception {
        AvroConverterTest.Utils.oneField(v, expected, Schema.create(Schema.Type.NULL));
    }

    static Stream<Arguments> failedSource() {
        return Stream.of(
            Arguments.of(-42),
            Arguments.of(9223372036854775807L),
            Arguments.of("a"),
            Arguments.of("42"),
            Arguments.of(42.2D),
            Arguments.of(42.2F)
        );
    }

    @ParameterizedTest
    @MethodSource("failedSource")
    void failed(Object v) {
        AvroConverterTest.Utils.oneFieldFailed(v, Schema.create(Schema.Type.NULL));
    }
}
