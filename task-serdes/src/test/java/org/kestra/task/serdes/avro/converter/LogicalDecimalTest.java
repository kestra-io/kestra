package org.kestra.task.serdes.avro.converter;

import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.kestra.task.serdes.avro.AvroConverterTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

public class LogicalDecimalTest {
    static Stream<Arguments> source() {
        return Stream.of(
            Arguments.of("12.82", new BigDecimal("12.82"), 4, 2),
            Arguments.of("12.8", new BigDecimal("12.80"), 4, 2),
            Arguments.of(12.8F, new BigDecimal("12.80"), 4, 2),
            Arguments.of("12.828282", new BigDecimal("12.828282"), 8, 6),
            Arguments.of(12L, new BigDecimal("12.00"), 4, 2),
            Arguments.of(12, new BigDecimal("12.00"), 4, 2),
            Arguments.of(12.8444D, new BigDecimal("12.84"), 4, 2),
            Arguments.of(12.8444F, new BigDecimal("12.84"), 4, 2),
            Arguments.of("2019", new BigDecimal("2019"), 4, 0)
            );
    }

    @ParameterizedTest
    @MethodSource("source")
    void convert(Object v, BigDecimal expected, Integer precision, Integer scale) throws Exception {
        Schema schema = LogicalTypes.decimal(precision, scale).addToSchema(Schema.create(Schema.Type.BYTES));
        AvroConverterTest.Utils.oneField(v, expected, schema);
    }
}
