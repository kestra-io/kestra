package org.kestra.task.serdes.avro.converter;

import org.apache.avro.Schema;
import org.kestra.task.serdes.avro.AvroConverterTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

public class PrimitiveIntTest {
    static Stream<Arguments> source() {
        return Stream.of(
            Arguments.of(-42, -42),
            Arguments.of("-42", -42),
            Arguments.of(42, 42),
            Arguments.of("42", 42)
        );
    }

    @ParameterizedTest
    @MethodSource("source")
    void convert(Object v, int expected) throws Exception {
        AvroConverterTest.Utils.oneField(v, expected, Schema.create(Schema.Type.INT));
    }

    static Stream<Arguments> failedSource() {
        return Stream.of(
            Arguments.of("-42.2"),
            Arguments.of(42.2D),
            Arguments.of(42.2F),
            Arguments.of("a"),
            Arguments.of(9223372036854775807L)
        );
    }

    @ParameterizedTest
    @MethodSource("failedSource")
    void failed(Object v) {
        AvroConverterTest.Utils.oneFieldFailed(v, Schema.create(Schema.Type.INT));
    }
}
