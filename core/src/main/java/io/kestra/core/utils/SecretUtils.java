package io.kestra.core.utils;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility for validating fields annotated with {@link PluginProperty#secret()}.
 */
public final class SecretUtils {

    private SecretUtils() {}

    /**
     * Returns a list of warning messages for any {@link PluginProperty#secret()} field
     * on the given object whose value is a plain-text string rather than a Pebble expression.
     *
     * @param obj the object to inspect (task, trigger, etc.)
     * @return warning messages, empty if all secret fields use Pebble expressions
     */
    public static List<String> validateSecretFields(Object obj) {
        List<String> warnings = new ArrayList<>();
        if (obj == null) {
            return warnings;
        }

        Class<?> clazz = obj.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                PluginProperty annotation = field.getAnnotation(PluginProperty.class);
                if (annotation == null || !annotation.secret()) {
                    continue;
                }

                field.setAccessible(true);
                Object fieldValue;
                try {
                    fieldValue = field.get(obj);
                } catch (IllegalAccessException e) {
                    continue;
                }

                if (fieldValue == null) {
                    continue;
                }

                String strValue;
                if (fieldValue instanceof String s) {
                    strValue = s;
                } else if (fieldValue instanceof Property<?> p) {
                    strValue = getPropertyExpression(p);
                } else {
                    continue;
                }

                if (strValue != null && !PebbleUtil.containsOpeningBlockDelimiter(strValue)) {
                    warnings.add("Property '" + field.getName() + "' is annotated as a secret and should be provided as a Pebble expression (e.g., `{{ secret('MY_SECRET') }}`), not a plain-text value.");
                }
            }
            clazz = clazz.getSuperclass();
        }

        return warnings;
    }

    private static String getPropertyExpression(Property<?> property) {
        try {
            Field expressionField = Property.class.getDeclaredField("expression");
            expressionField.setAccessible(true);
            return (String) expressionField.get(property);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return null;
        }
    }
}
