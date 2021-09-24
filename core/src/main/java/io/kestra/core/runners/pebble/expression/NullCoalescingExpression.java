package io.kestra.core.runners.pebble.expression;

import com.mitchellbosecke.pebble.error.AttributeNotFoundException;
import com.mitchellbosecke.pebble.node.expression.BinaryExpression;
import com.mitchellbosecke.pebble.node.expression.Expression;
import com.mitchellbosecke.pebble.template.EvaluationContextImpl;
import com.mitchellbosecke.pebble.template.PebbleTemplateImpl;

public class NullCoalescingExpression extends BinaryExpression<Object> {
    public NullCoalescingExpression() {
    }

    public NullCoalescingExpression(Expression<?> left, Expression<?> right) {
        super(left, right);
    }

    @Override
    public Object evaluate(PebbleTemplateImpl self, EvaluationContextImpl context) {
        try {
            return getLeftExpression().evaluate(self, context);
        } catch (AttributeNotFoundException e) {
            return getRightExpression().evaluate(self, context);
        }
    }

    @Override
    public String toString() {
        return String.format("%s ?? %s", getLeftExpression(), getRightExpression());
    }

}
