package io.kestra.core.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class MathUtils {
    /**
     * Rounds a double value to a specified number of decimal places.
     *
     * @param value The double value to be rounded.
     * @param decimalPlaces The number of decimal places to round to.
     * Must be a non-negative integer.
     * @return The rounded double value.
     * @throws IllegalArgumentException If decimalPlaces is negative.
     */
    public static double roundDouble(double value, int decimalPlaces) {
        if (decimalPlaces < 0) {
            throw new IllegalArgumentException("The number of decimal places must be non-negative.");
        }
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(decimalPlaces, RoundingMode.HALF_UP);

        return bd.doubleValue();
    }
}
