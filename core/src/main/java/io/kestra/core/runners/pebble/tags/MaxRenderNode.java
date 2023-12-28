package io.kestra.core.runners.pebble.tags;

import io.pebbletemplates.pebble.extension.NodeVisitor;
import io.pebbletemplates.pebble.node.AbstractRenderableNode;
import io.pebbletemplates.pebble.node.BodyNode;
import io.pebbletemplates.pebble.node.expression.Expression;
import io.pebbletemplates.pebble.template.EvaluationContextImpl;
import io.pebbletemplates.pebble.template.PebbleTemplateImpl;

import java.io.IOException;
import java.io.Writer;

import static io.pebbletemplates.pebble.utils.TypeUtils.compatibleCast;

/**
 * Used to limit the amount of recursive rendering done, it represents a {% maxRender 2 %}{{mustBeRendered2Times}}{% endmaxRender %} tag block.
 */
public class MaxRenderNode extends AbstractRenderableNode {

    private final Expression<?> renderingCountLeft;
    private final BodyNode parsedBody;

    public MaxRenderNode(int lineNumber, Expression<?> renderingCountLeft, BodyNode parsedBody) {
        super(lineNumber);
        this.renderingCountLeft = renderingCountLeft;
        this.parsedBody = parsedBody;
    }

    @Override
    public void render(PebbleTemplateImpl self, Writer writer, EvaluationContextImpl context)
        throws IOException {
        int renderingLeft = compatibleCast(this.renderingCountLeft.evaluate(self, context), Integer.class) - 1;
        String tag = renderingLeft > 0
            ? "maxRender"
            : "raw";
        writer.append("{% ")
            .append(tag);
        if (renderingLeft > 0) {
            writer.append(" ")
                .append(String.valueOf(renderingLeft));
        }
        writer.append(" %}");
        this.parsedBody.render(self, writer, context);
        writer.append("{% end")
            .append(tag)
            .append(" %}");
    }

    @Override
    public void accept(NodeVisitor visitor) {
        visitor.visit(this);
    }
}
