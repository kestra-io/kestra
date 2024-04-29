package io.kestra.core.runners.pebble.expression;

import io.pebbletemplates.pebble.node.expression.BinaryExpression;
import io.pebbletemplates.pebble.template.EvaluationContextImpl;
import io.pebbletemplates.pebble.template.PebbleTemplateImpl;

import java.util.Collection;

/**
 * Expression for testing that a value is not in a given collection, map, or array.
 *<pre>
 *Example: {{ 'baz' not in ['foo', 'bar'] }}
 *</pre>
 *
 * @see io.pebbletemplates.pebble.node.expression.ContainsExpression
 */
public class NotInExpression extends InExpression {


    /** {@inheritDoc} **/
    @Override
    public Boolean evaluate(final PebbleTemplateImpl self,
                            final EvaluationContextImpl context) {
        return !super.evaluate(self, context);
    }

    /** {@inheritDoc} **/
    @Override
    public String toString() {
        return String.format("%s not in %s", getLeftExpression(), getRightExpression());
    }
}
