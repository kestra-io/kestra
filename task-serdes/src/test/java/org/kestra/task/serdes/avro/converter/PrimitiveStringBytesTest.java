package org.kestra.task.serdes.avro.converter;

import org.apache.avro.Schema;
import org.apache.avro.util.Utf8;
import org.kestra.task.serdes.avro.AvroConverterTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;
import java.util.stream.Stream;

public class PrimitiveStringBytesTest {
    static Stream<Arguments> source() {
        return Stream.of(
            Arguments.of("a", "a"),
            Arguments.of("true", "true"),
            Arguments.of(null, "null"),
            Arguments.of(1, "1"),
            Arguments.of(42D, "42.0"),
            Arguments.of(42F, "42.0"),
            Arguments.of(42L, "42"),
            Arguments.of(42.0D, "42.0"),
            Arguments.of("", "")
        );
    }

    @ParameterizedTest
    @MethodSource("source")
    void convert(Object v, String expected) throws Exception {
        AvroConverterTest.Utils.oneField(v, new Utf8(expected.getBytes()), Schema.create(Schema.Type.STRING));
    }

    @ParameterizedTest
    @MethodSource("source")
    static void convertBytes(Object v, String expected) throws Exception {
        AvroConverterTest.Utils.oneField(v, ByteBuffer.wrap(new Utf8(expected.getBytes()).getBytes()), Schema.create(Schema.Type.BYTES));
    }
}
