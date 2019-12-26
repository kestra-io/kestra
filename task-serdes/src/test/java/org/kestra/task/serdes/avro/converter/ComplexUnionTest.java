package org.kestra.task.serdes.avro.converter;

import org.apache.avro.Schema;
import org.kestra.task.serdes.avro.AvroConverterTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ComplexUnionTest {
    static Stream<Arguments> source() {
        return Stream.of(
            Arguments.of("null", Arrays.asList(Schema.Type.NULL, Schema.Type.BOOLEAN),  null),
            Arguments.of("null", Arrays.asList(Schema.Type.BOOLEAN, Schema.Type.NULL),  null),
            Arguments.of("1", Arrays.asList(Schema.Type.INT, Schema.Type.NULL), 1)
        );
    }

    @ParameterizedTest
    @MethodSource("source")
    static void convert(Object v, List<Schema.Type> schemas, Object expected) throws Exception {
        AvroConverterTest.Utils.oneField(v, expected, Schema.createUnion(schemas
            .stream()
            .map(Schema::create)
            .collect(Collectors.toList())
        ));
    }
}
