package io.kestra.core.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

class EnumsTest {

    @Test
    void shouldGetEnumForNameIgnoreCaseForExisting() {
        TestEnum result = Enums.getForNameIgnoreCase("enum1", TestEnum.class);
        Assertions.assertEquals(TestEnum.ENUM1, result);
    }

    @Test
    void shouldGetEnumForNameIgnoreCaseForFallback() {
        TestEnum result = Enums.getForNameIgnoreCase("LEGACY", TestEnum.class, Map.of("legacy", TestEnum.ENUM2));
        Assertions.assertEquals(TestEnum.ENUM2, result);
    }

    @Test
    void shouldThrowExceptionGivenInvalidString() {
        assertThrows(IllegalArgumentException.class, () -> {
            Enums.getForNameIgnoreCase("invalid", TestEnum.class);
        });
    }

    @Test
    void testFromStringValidMapping() {
        // GIVEN
        Map<String, TestEnumWithValue> mapping = TestEnumWithValue.getMapping();
        String validValue = "enum1";

        // WHEN
        TestEnumWithValue result = Enums.fromString(validValue, mapping, "TestEnumWithValue");

        // THEN
        Assertions.assertEquals(TestEnumWithValue.ENUM1, result);
    }
    @Test
    void testFromStringInvalidValue() {
        // Arrange
        Map<String, TestEnumWithValue> mapping = TestEnumWithValue.getMapping();
        String invalidValue = "invalidValue";

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            Enums.fromString(invalidValue, mapping, "TestEnumWithValue")
        );
    }

    @Test
    void should_get_from_list(){
        assertThat(Enums.fromList(List.of(TestEnum.ENUM1, TestEnum.ENUM2), TestEnum.class)).isEqualTo(List.of(TestEnum.ENUM1, TestEnum.ENUM2));
        assertThat(Enums.fromList(List.of("ENUM1", "ENUM2"), TestEnum.class)).isEqualTo(List.of(TestEnum.ENUM1, TestEnum.ENUM2));
        assertThat(Enums.fromList(TestEnum.ENUM1, TestEnum.class)).isEqualTo(List.of(TestEnum.ENUM1));
        assertThat(Enums.fromList("ENUM1", TestEnum.class)).isEqualTo(List.of(TestEnum.ENUM1));

        assertThrows(IllegalArgumentException.class, () -> Enums.fromList(List.of("string1", "string2"), TestEnum.class));
        assertThrows(IllegalArgumentException.class, () -> Enums.fromList("non enum value", TestEnum.class));
    }

    enum TestEnum {
        ENUM1, ENUM2
    }
    enum TestEnumWithValue {
        ENUM1("enum1"),
        ENUM2("enum2");

        private static final Map<String, TestEnumWithValue> BY_VALUE = Arrays.stream(values())
            .collect(Collectors.toMap(TestEnumWithValue::getValue, Function.identity()));

        private final String value;

        TestEnumWithValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static Map<String, TestEnumWithValue> getMapping() {
            return BY_VALUE;
        }
    }
}