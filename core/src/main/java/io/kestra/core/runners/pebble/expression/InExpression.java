package io.kestra.core.runners.pebble.expression;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.node.expression.BinaryExpression;
import io.pebbletemplates.pebble.template.EvaluationContextImpl;
import io.pebbletemplates.pebble.template.PebbleTemplateImpl;

import java.util.Collection;
import java.util.Map;

/**
 * Expression for testing that a value is in a given collection, map, or array.
 * <pre>
 * Example: {{ 'foo' in ['foo', 'bar'] }}
 * </pre>
 *
 * @see io.pebbletemplates.pebble.node.expression.ContainsExpression
 */
public class InExpression extends BinaryExpression<Boolean> {

    /**
     * {@inheritDoc}
     **/
    @Override
    @SuppressWarnings({"rawtypes"})
    public Boolean evaluate(
        final PebbleTemplateImpl self,
        final EvaluationContextImpl context) {
        Object left = getLeftExpression().evaluate(self, context);
        Object right = getRightExpression().evaluate(self, context);

        if (right == null) {
            return false;
        }

        return switch (right) {
            case Collection collection -> isInCollection(collection, left);
            case Map map -> map.containsKey(left);
            case Object[] objects -> containsObject(objects, left);
            case boolean[] booleans -> containsBoolean(booleans, left);
            case byte[] bytes -> containsByte(bytes, left);
            case char[] chars -> containsChar(chars, left);
            case double[] doubles -> containsDouble(doubles, left);
            case float[] floats -> containsFloat(floats, left);
            case int[] ints -> containsInt(ints, left);
            case long[] longs -> containsLong(longs, left);
            case short[] longs -> containsShort(longs, left);
            default -> throw new PebbleException(null,
                "In operator can only be used on Collections, Maps and arrays. Actual type was: "
                    + right.getClass().getName(), this.getLineNumber(), self.getName());
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean isInCollection(Collection collection, Object left) {
        return left instanceof Collection<?> colLeft ?
            collection.containsAll(colLeft) :
            collection.contains(left);
    }

    private static boolean containsObject(Object[] array, Object value) {
        for (Object obj : array) {
            if (obj != null && obj.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsBoolean(boolean[] array, Object value) {
        if (value instanceof Boolean) {
            boolean target = (boolean) value;
            for (boolean element : array) {
                if (element == target) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsByte(byte[] array, Object value) {
        if (value instanceof Byte) {
            byte target = (byte) value;
            for (byte element : array) {
                if (element == target) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsChar(char[] array, Object value) {
        if (value instanceof Character) {
            char target = (char) value;
            for (char element : array) {
                if (element == target) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsDouble(double[] array, Object value) {
        if (value instanceof Double) {
            double target = (double) value;
            for (double element : array) {
                if (element == target) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsFloat(float[] array, Object value) {
        if (value instanceof Float) {
            float target = (float) value;
            for (float element : array) {
                if (element == target) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsInt(int[] array, Object value) {
        if (value instanceof Integer) {
            int target = (int) value;
            for (int element : array) {
                if (element == target) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsLong(long[] array, Object value) {
        if (value instanceof Long) {
            long target = (long) value;
            for (long element : array) {
                if (element == target) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsShort(short[] array, Object value) {
        if (value instanceof Short) {
            short target = (short) value;
            for (short element : array) {
                if (element == target) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public String toString() {
        return String.format("%s in %s", getLeftExpression(), getRightExpression());
    }
}
