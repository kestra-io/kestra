package io.kestra.core.utils;

import java.util.List;

abstract public class TruthUtils {
    private static final List<String> FALSE_VALUES = List.of("false", "0", "-0", "");

    public static boolean isTruthy(String condition) {
        return condition != null && !FALSE_VALUES.contains(condition);
    }

    public static boolean isFalsy(String condition) {
        return condition != null && FALSE_VALUES.contains(condition);
    }
}
