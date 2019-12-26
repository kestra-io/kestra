package org.kestra.task.serdes.avro.converter;

import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.kestra.task.serdes.avro.AvroConverterTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.UUID;
import java.util.stream.Stream;

public class LogicalUuidTest {
    private Schema schema = LogicalTypes.uuid().addToSchema(Schema.create(Schema.Type.STRING));

    static Stream<Arguments> source() {
        return Stream.of(
            Arguments.of("123e4567-e89b-12d3-a456-556642440000", UUID.fromString("123e4567-e89b-12d3-a456-556642440000"))
        );
    }

    @ParameterizedTest
    @MethodSource("source")
    void convert(CharSequence v, UUID expected) throws Exception {
        AvroConverterTest.Utils.oneField(v, expected, schema);
    }

    static Stream<Arguments> failedSource() {
        return Stream.of(
            Arguments.of("123e4567"),
            Arguments.of("123e4567e89b12d3a456556642440000")
        );
    }

    @ParameterizedTest
    @MethodSource("failedSource")
    void failed(Object v) {
        AvroConverterTest.Utils.oneFieldFailed(v, schema);
    }
}
