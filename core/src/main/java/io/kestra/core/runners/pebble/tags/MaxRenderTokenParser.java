package io.kestra.core.runners.pebble.tags;

import io.pebbletemplates.pebble.lexer.Token;
import io.pebbletemplates.pebble.lexer.TokenStream;
import io.pebbletemplates.pebble.node.*;
import io.pebbletemplates.pebble.node.expression.Expression;
import io.pebbletemplates.pebble.parser.Parser;
import io.pebbletemplates.pebble.tokenParser.TokenParser;

public class MaxRenderTokenParser implements TokenParser {

	public String getTag(){
		return "maxRender";
	}

	@Override
	public RenderableNode parse(Token token, Parser parser) {
		TokenStream stream = parser.getStream();
		int lineNumber = token.getLineNumber();

        Expression<?> renderingCountLeft = null;
        if(!stream.next().test(Token.Type.EXECUTE_END)) {
            // use the built in expression parser to parse the variable name
            renderingCountLeft = parser.getExpressionParser().parseExpression();
        }

        // expect to see "%}"
        stream.expect(Token.Type.EXECUTE_END);

        BodyNode parsedBody = parser.subparse(node ->
            node.test(Token.Type.NAME, "endmaxRender")
        );

        // skip endraw tag
        stream.next();

        stream.expect(Token.Type.EXECUTE_END);

        // RawNode is composed of a remaining and a value
		return new MaxRenderNode(lineNumber, renderingCountLeft, parsedBody);
	}


}