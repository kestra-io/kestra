package io.kestra.core.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MathUtilsTest {
    @Test
    void shouldRoundDoubleCorrectly() {
        assertThat(MathUtils.roundDouble(123.456, 2)).isEqualTo(123.46);
        assertThat(MathUtils.roundDouble(123.454, 2)).isEqualTo(123.45);
        assertThat(MathUtils.roundDouble(123.455, 2)).isEqualTo(123.46); 
        assertThat(MathUtils.roundDouble(123.4, 0)).isEqualTo(123.0);
        assertThat(MathUtils.roundDouble(123.5, 0)).isEqualTo(124.0);
        assertThat(MathUtils.roundDouble(0.0, 5)).isEqualTo(0.0);
    }

    @Test
    void shouldThrowExceptionWhenDecimalPlacesIsNegative() {
        assertThatThrownBy(() -> MathUtils.roundDouble(123.456, -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("The number of decimal places must be non-negative.");
    }
}
