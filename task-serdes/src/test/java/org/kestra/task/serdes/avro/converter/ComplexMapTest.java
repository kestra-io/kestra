package org.kestra.task.serdes.avro.converter;

import com.google.common.collect.ImmutableMap;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.util.Utf8;
import org.kestra.task.serdes.avro.AvroConverterTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

public class ComplexMapTest {
    static Stream<Arguments> source() {
        return Stream.of(
            Arguments.of(
                ImmutableMap.of("a", 42.2D, "b", "42", "c", 42.2D),
                ImmutableMap.of(new Utf8("a".getBytes()), 42.2F, new Utf8("b".getBytes()), 42F, new Utf8("c".getBytes()), 42.2F),
                Schema.create(Schema.Type.FLOAT))
        );
    }

    @ParameterizedTest
    @MethodSource("source")
    void convert(Object v, Map<Utf8, Object> expected, Schema type) throws Exception {
        AvroConverterTest.Utils.oneField(v, expected, SchemaBuilder.map().values(type));
    }

    static Stream<Arguments> failedSource() {
        return Stream.of(
            Arguments.of(ImmutableMap.of("a", 42.2D, "b", "a"), Schema.create(Schema.Type.FLOAT)),
            Arguments.of(ImmutableMap.of("a", "null", "b", "a"), Schema.createUnion(Schema.create(Schema.Type.BOOLEAN), Schema.create(Schema.Type.NULL)))
        );
    }

    @ParameterizedTest
    @MethodSource("failedSource")
    void failed(Object v, Schema type) {
        AvroConverterTest.Utils.oneFieldFailed(v, SchemaBuilder.map().values(type));
    }
}
