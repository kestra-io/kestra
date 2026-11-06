package io.kestra.core.runners.pebble;

import java.io.StringReader;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.extension.ExtensionRegistry;
import io.pebbletemplates.pebble.lexer.LexerImpl;
import io.pebbletemplates.pebble.node.PrintNode;
import io.pebbletemplates.pebble.node.expression.Expression;
import io.pebbletemplates.pebble.parser.ParserImpl;
import io.pebbletemplates.pebble.parser.ParserOptions;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class PebbleExpressionPrinterTest {
    @Inject
    PebbleEngineFactory pebbleEngineFactory;

    static Stream<Arguments> expressions() {
        return Stream.of(
            Arguments.of("outputs . fetch['values'][\"code\"]", "outputs.fetch.values.code"),
            Arguments.of("outputs['my-task'].value", "outputs['my-task'].value"),
            Arguments.of("items[0].name", "items[0].name"),
            Arguments.of("code|trim|upper", "code | trim | upper"),
            Arguments.of("day | date('yyyy-MM-dd')", "day | date('yyyy-MM-dd')"),
            Arguments.of("code != \"KR-0412\"", "code != 'KR-0412'"),
            Arguments.of("a + b * c", "a + (b * c)"),
            Arguments.of("not (a and b)", "not (a and b)"),
            Arguments.of("x ?? 'none'", "x ?? 'none'"),
            Arguments.of("x is not null", "x is not null"),
            Arguments.of("list contains 'it\\'s'", "list contains 'it\\'s'"),
            Arguments.of("flag ? {'a': 1} : [1, 2.5]", "flag ? {'a': 1} : [1, 2.5]"),
            Arguments.of("secret('KEY', namespace=flow.namespace)", "secret('KEY', namespace=flow.namespace)")
        );
    }

    @ParameterizedTest
    @MethodSource("expressions")
    void shouldPrintTheSameTextForTheSameExpressionWhateverItsSpacingOrQuotes(String written, String printed) {
        PebbleEngine engine = pebbleEngineFactory.create();
        PebbleExpressionPrinter printer = new PebbleExpressionPrinter(engine.getExtensionRegistry());

        assertThat(printer.print(parse(engine, written))).contains(printed);
        assertThat(printer.print(parse(engine, printed))).contains(printed);
    }

    private static Expression<?> parse(PebbleEngine engine, String expression) {
        ExtensionRegistry registry = engine.getExtensionRegistry();
        LexerImpl lexer = new LexerImpl(engine.getSyntax(), registry.getUnaryOperators().values(), registry.getBinaryOperators().values());
        ParserImpl parser = new ParserImpl(registry.getUnaryOperators(), registry.getBinaryOperators(), registry.getTokenParsers(), new ParserOptions());
        PrintNode print = (PrintNode) parser.parse(lexer.tokenize(new StringReader("{{ " + expression + " }}"), "test")).getBody().getChildren().getFirst();
        return print.getExpression();
    }
}
