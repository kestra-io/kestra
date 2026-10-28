package io.kestra.core.runners.pebble;

import java.util.Map;
import java.util.Optional;

import io.pebbletemplates.pebble.extension.AbstractNodeVisitor;
import io.pebbletemplates.pebble.extension.NodeVisitor;
import io.pebbletemplates.pebble.extension.NodeVisitorFactory;
import io.pebbletemplates.pebble.node.PrintNode;
import io.pebbletemplates.pebble.node.RootNode;
import io.pebbletemplates.pebble.node.expression.Expression;
import io.pebbletemplates.pebble.template.EvaluationContextImpl;
import io.pebbletemplates.pebble.template.PebbleTemplateImpl;
import lombok.Getter;

/**
 * The expression of a top-level {@code {{ }}} block, which renders the value a replay set for it in place of its own.
 * The values are read from the {@link #VARIABLE} variable, keyed by the expression as {@link PebbleExpressionPrinter} prints it.
 */
public class OverridableExpression implements Expression<Object> {
    public static final String VARIABLE = "replay-overrides";
    public static final NodeVisitorFactory VISITOR = template -> new AbstractNodeVisitor((PebbleTemplateImpl) template) {
        @Override
        public void visit(RootNode root) {
            root.getBody().getChildren().forEach(child ->
            {
                if (child instanceof PrintNode print && !(print.getExpression() instanceof OverridableExpression)) {
                    print.setExpression(new OverridableExpression(print.getExpression()));
                }
            });
        }
    };

    @Getter
    private final Expression<?> expression;
    private volatile Optional<String> key;

    private OverridableExpression(Expression<?> expression) {
        this.expression = expression;
    }

    @Override
    public Object evaluate(PebbleTemplateImpl self, EvaluationContextImpl context) {
        if (context.getVariable(VARIABLE) instanceof Map<?, ?> overrides && !overrides.isEmpty()) {
            Optional<String> printed = key(context);
            if (printed.isPresent() && overrides.containsKey(printed.get())) {
                return overrides.get(printed.get());
            }
        }
        return expression.evaluate(self, context);
    }

    @Override
    public void accept(NodeVisitor visitor) {
        expression.accept(visitor);
    }

    @Override
    public int getLineNumber() {
        return expression.getLineNumber();
    }

    private Optional<String> key(EvaluationContextImpl context) {
        if (key == null) {
            key = new PebbleExpressionPrinter(context.getExtensionRegistry()).print(expression);
        }
        return key;
    }
}
