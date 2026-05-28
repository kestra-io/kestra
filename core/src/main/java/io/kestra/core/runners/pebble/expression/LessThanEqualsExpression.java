package io.kestra.core.runners.pebble.expression;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.node.expression.BinaryExpression;
import io.pebbletemplates.pebble.template.EvaluationContextImpl;
import io.pebbletemplates.pebble.template.PebbleTemplateImpl;
import io.pebbletemplates.pebble.utils.OperatorUtils;

public class LessThanEqualsExpression extends BinaryExpression<Boolean> {
    @Override
    public Boolean evaluate(PebbleTemplateImpl self, EvaluationContextImpl context) {
        Object left = this.getLeftExpression().evaluate(self, context);
        Object right = this.getRightExpression().evaluate(self, context);

        // add support for string comparison
        if (left instanceof String sLeft && right instanceof String sRight) {
            return sLeft.compareTo(sRight) <= 0;
        }

        // default implementation from io.pebbletemplates.pebble.node.expression.LessThanExpression
        try {
            return OperatorUtils.lte(left, right);
        } catch (Exception ex) {
            throw new PebbleException(ex, "Could not perform less than or equals comparison", this.getLineNumber(), self
                .getName());
        }
    }
}
