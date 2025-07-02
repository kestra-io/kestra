package io.kestra.core.runners.pebble.expression;

import io.pebbletemplates.pebble.node.expression.BinaryExpression;
import io.pebbletemplates.pebble.node.expression.Expression;
import io.pebbletemplates.pebble.template.PebbleTemplateImpl;
import io.pebbletemplates.pebble.template.EvaluationContextImpl;

import java.util.Collection;
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

        if (rightValue instanceof Collection<?>) {
            return ((Collection<?>) rightValue).stream().map(Object::toString).toList().contains(leftValue.toString());
        }
        if (rightValue instanceof Iterable<?>) {
            for (Object item : (Iterable<?>) rightValue) {
                if (Objects.equals(item.toString(), leftValue.toString())) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("%s in %s", getLeftExpression(), getRightExpression());
    }
}