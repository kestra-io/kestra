package io.kestra.core.runners.pebble.expression;

import io.pebbletemplates.pebble.node.expression.BinaryExpression;
import io.pebbletemplates.pebble.node.expression.Expression;
import io.pebbletemplates.pebble.template.EvaluationContextImpl;
import io.pebbletemplates.pebble.template.PebbleTemplateImpl;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

public class InExpression extends BinaryExpression<Object> {
    public InExpression() {
    }

    public InExpression(Expression<?> left, Expression<?> right) {
        super(left, right);
    }

    @Override
    public Object evaluate(PebbleTemplateImpl self, EvaluationContextImpl context) {
        Object leftValue = getLeftExpression().evaluate(self, context);
        Object rightValue = getRightExpression().evaluate(self, context);

        if (leftValue == null || rightValue == null) {
            return false;
        }

        switch (rightValue) {
            case Collection<?> objects -> {
                return objects.stream().map(Object::toString).toList().contains(leftValue.toString());
            }
            case Iterable<?> objects -> {
                for (Object item : objects) {
                    if (Objects.equals(item.toString(), leftValue.toString())) {
                        return true;
                    }
                }
                return false;
            }
            case Map<?, ?> map -> {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (Objects.equals(entry.getKey().toString(), leftValue.toString()) ||
                            Objects.equals(entry.getValue().toString(), leftValue.toString())) {
                        return true;
                    }
                }
            }
            default -> {
                return false;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("%s isIn %s", getLeftExpression(), getRightExpression());
    }
}