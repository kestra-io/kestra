package io.kestra.core.runners.pebble.tags;

import io.pebbletemplates.pebble.extension.NodeVisitor;
import io.pebbletemplates.pebble.node.AbstractRenderableNode;
import io.pebbletemplates.pebble.node.BodyNode;
import io.pebbletemplates.pebble.node.expression.Expression;
import io.pebbletemplates.pebble.template.EvaluationContextImpl;
import io.pebbletemplates.pebble.template.PebbleTemplateImpl;

import java.io.IOException;
import java.io.Writer;

// Represents a {% raw 2 %}{{mustBeRendered2Times}}{% endraw %} tag block
public class RawNode extends AbstractRenderableNode {

    private final Expression<?> renderingCountLeft;
    private final BodyNode parsedBody;

    public RawNode(int lineNumber, Expression<?> renderingCountLeft, BodyNode parsedBody) {
        super(lineNumber);
        this.renderingCountLeft = renderingCountLeft;
        this.parsedBody = parsedBody;
    }

    @Override
    public void render(PebbleTemplateImpl self, Writer writer, EvaluationContextImpl context)
        throws IOException {
        writer.append("{% raw ")
            .append(String.valueOf(this.renderingCountLeft.evaluate(self, context)))
            .append(" %}");
        this.parsedBody.render(self, writer, context);
        writer.append("{% endraw %}");
    }

    @Override
    public void accept(NodeVisitor visitor) {
        visitor.visit(this);
    }
}
