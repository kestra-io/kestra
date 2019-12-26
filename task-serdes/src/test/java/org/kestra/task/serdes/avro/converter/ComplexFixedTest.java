package org.kestra.task.serdes.avro.converter;

import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.kestra.task.serdes.avro.AvroConverterTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

public class ComplexFixedTest {
    static Stream<Arguments> source() {
        return Stream.of(
            Arguments.of("a", "a", 1),
            Arguments.of("ž", "ž", 2),
            Arguments.of("ࠀ", "ࠀ", 3),
            Arguments.of("\uD83D\uDCA9", "\uD83D\uDCA9", 4)
        );
    }

    @ParameterizedTest
    @MethodSource("source")
    void convert(Object v, String expected, int length) throws Exception {
        Schema schema = SchemaBuilder.fixed("fixed").size(length);

        AvroConverterTest.Utils.oneField(v, new GenericData.Fixed(schema, expected.getBytes()), schema);
    }

    static Stream<Arguments> failedSource() {
        return Stream.of(
            Arguments.of("a", 3),
            Arguments.of("", 1),
            Arguments.of("ࠀ", 2)
        );
    }

    @ParameterizedTest
    @MethodSource("failedSource")
    void failed(Object v, int length) {
        AvroConverterTest.Utils.oneFieldFailed(v, SchemaBuilder.fixed("fixed").size(length));
    }
}
