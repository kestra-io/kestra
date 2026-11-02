package io.kestra.core.runners.pebble;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.pebbletemplates.pebble.extension.AbstractNodeVisitor;
import io.pebbletemplates.pebble.extension.NodeVisitor;
import io.pebbletemplates.pebble.extension.NodeVisitorFactory;
import io.pebbletemplates.pebble.node.BodyNode;
import io.pebbletemplates.pebble.node.IfNode;
import io.pebbletemplates.pebble.node.PrintNode;
import io.pebbletemplates.pebble.node.RenderableNode;
import io.pebbletemplates.pebble.node.RootNode;
import io.pebbletemplates.pebble.node.expression.Expression;
import io.pebbletemplates.pebble.template.EvaluationContextImpl;
import io.pebbletemplates.pebble.template.PebbleTemplateImpl;
import lombok.Getter;

/**
 * The expression of an overridable {@code {{ }}} block, which renders the value a replay set for it in place of its own.
 * The values are read from the {@link #VARIABLE} variable, keyed by the expression as {@link PebbleExpressionPrinter} prints it.
 */
public class OverridableExpression implements Expression<Object> {
    public static final String VARIABLE = "replay-overrides";
    public static final NodeVisitorFactory VISITOR = template -> new AbstractNodeVisitor((PebbleTemplateImpl) template) {
        @Override
        public void visit(RootNode root) {
            overridablePrints(root).forEach(print ->
            {
                if (!(print.getExpression() instanceof OverridableExpression)) {
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

    /**
     * The prints a replay can override: at the top level, or in the branches of an {@code if} reached from there.
     * Loops, {@code set} and macros are left out, since their prints can read variables that only exist inside them.
     */
    public static List<PrintNode> overridablePrints(RootNode root) {
        List<PrintNode> prints = new ArrayList<>();
        collect(root.getBody(), prints);
        return prints;
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

    private static void collect(BodyNode body, List<PrintNode> prints) {
        for (RenderableNode child : body.getChildren()) {
            if (child instanceof PrintNode print) {
                prints.add(print);
            } else if (child instanceof IfNode ifNode) {
                ifNode.getConditionsWithBodies().forEach(branch -> collect(branch.getRight(), prints));
                if (ifNode.getElseBody() != null) {
                    collect(ifNode.getElseBody(), prints);
                }
            }
        }
    }

    private Optional<String> key(EvaluationContextImpl context) {
        if (key == null) {
            key = new PebbleExpressionPrinter(context.getExtensionRegistry()).print(expression);
        }
        return key;
    }
}
