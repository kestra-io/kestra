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
        // Validate the input for decimalPlaces
        if (decimalPlaces < 0) {
            throw new IllegalArgumentException("The number of decimal places must be non-negative.");
        }

        // Convert the double to a BigDecimal using its String representation.
        // This is crucial to avoid floating-point precision issues that can occur
        // when a double is directly converted to BigDecimal or when performing
        // arithmetic operations on doubles.
        BigDecimal bd = new BigDecimal(String.valueOf(value));

        // Set the scale (number of decimal places) and apply the rounding mode.
        // RoundingMode.HALF_UP is a common rounding mode where:
        // - If the discarded fraction is >= 0.5, the digit to the left is rounded up.
        // - If the discarded fraction is < 0.5, the digit to the left is not changed.
        bd = bd.setScale(decimalPlaces, RoundingMode.HALF_UP);

        // Convert the BigDecimal back to a double and return it.
        return bd.doubleValue();
    }
}
