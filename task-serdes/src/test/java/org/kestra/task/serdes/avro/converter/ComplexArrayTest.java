package org.kestra.task.serdes.avro.converter;

import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.kestra.task.serdes.avro.AvroConverterTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class ComplexArrayTest {
    static Stream<Arguments> source() {
        return Stream.of(
            Arguments.of(Arrays.asList("42.2", 42.2D), Arrays.asList(42.2F, 42.2F), Schema.create(Schema.Type.FLOAT)),
            Arguments.of(Arrays.asList("null", "true", true, false, null), Arrays.asList(null, true, true, false, null), Schema.createUnion(Schema.create(Schema.Type.BOOLEAN), Schema.create(Schema.Type.NULL)))
        );
    }

    @ParameterizedTest
    @MethodSource("source")
    void convert(Object v, List<Object> expected, Schema type) throws Exception {
        AvroConverterTest.Utils.oneField(v, expected, SchemaBuilder.array().items(type));
    }

    static Stream<Arguments> failedSource() {
        return Stream.of(
            Arguments.of(Arrays.asList("a", 42.2), Schema.create(Schema.Type.FLOAT)),
            Arguments.of(Arrays.asList("null", "a"), Schema.createUnion(Schema.create(Schema.Type.BOOLEAN), Schema.create(Schema.Type.NULL)))

        );
    }

    @ParameterizedTest
    @MethodSource("failedSource")
    void failed(Object v, Schema type) {
        AvroConverterTest.Utils.oneFieldFailed(v, SchemaBuilder.array().items(type));
    }
}
