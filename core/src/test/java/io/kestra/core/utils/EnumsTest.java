package io.kestra.core.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

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
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            Enums.getForNameIgnoreCase("invalid", TestEnum.class);
        });
    }

    enum TestEnum {
        ENUM1, ENUM2
    }
}