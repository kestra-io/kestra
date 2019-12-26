package org.kestra.task.serdes.avro.converter;

import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.kestra.task.serdes.avro.AvroConverterTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class ComplexEnumTest {
    static Stream<Arguments> source() {
        return Stream.of(
            Arguments.of("a", "a", Arrays.asList("a", "b", "c")),
            Arguments.of("ž", "ž", Arrays.asList("a", "ž", "c")),
            Arguments.of("ࠀ", "ࠀ", Arrays.asList("a", "b", "ࠀ"))
        );
    }

    @ParameterizedTest
    @MethodSource("source")
    void convert(Object v, String expected, List<String> values) throws Exception {
        Schema schema = SchemaBuilder.enumeration("enumeration").symbols(values.toArray(String[]::new));
        AvroConverterTest.Utils.oneField(v, new GenericData.EnumSymbol(schema, expected), schema);
    }

    static Stream<Arguments> failedSource() {
        return Stream.of(
            Arguments.of("", Arrays.asList("a", "b", "c")),
            Arguments.of("ࠀ", Arrays.asList("a", "b", "c"))
        );
    }

    @ParameterizedTest
    @MethodSource("failedSource")
    void failed(Object v, List<String> values) {
        AvroConverterTest.Utils.oneFieldFailed(v, SchemaBuilder.enumeration("enumeration").symbols(values.toArray(String[]::new)));
    }
}
