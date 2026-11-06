package io.kestra.core.runners.pebble;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.kestra.core.utils.ListUtils;

import io.pebbletemplates.pebble.extension.ExtensionRegistry;
import io.pebbletemplates.pebble.node.ArgumentsNode;
import io.pebbletemplates.pebble.node.TestInvocationExpression;
import io.pebbletemplates.pebble.node.expression.ArrayExpression;
import io.pebbletemplates.pebble.node.expression.BinaryExpression;
import io.pebbletemplates.pebble.node.expression.ContextVariableExpression;
import io.pebbletemplates.pebble.node.expression.Expression;
import io.pebbletemplates.pebble.node.expression.FilterExpression;
import io.pebbletemplates.pebble.node.expression.FilterInvocationExpression;
import io.pebbletemplates.pebble.node.expression.FunctionOrMacroInvocationExpression;
import io.pebbletemplates.pebble.node.expression.GetAttributeExpression;
import io.pebbletemplates.pebble.node.expression.LiteralBigDecimalExpression;
import io.pebbletemplates.pebble.node.expression.LiteralBooleanExpression;
import io.pebbletemplates.pebble.node.expression.LiteralDoubleExpression;
import io.pebbletemplates.pebble.node.expression.LiteralIntegerExpression;
import io.pebbletemplates.pebble.node.expression.LiteralLongExpression;
import io.pebbletemplates.pebble.node.expression.LiteralNullExpression;
import io.pebbletemplates.pebble.node.expression.LiteralStringExpression;
import io.pebbletemplates.pebble.node.expression.MapExpression;
import io.pebbletemplates.pebble.node.expression.RenderableNodeExpression;
import io.pebbletemplates.pebble.node.expression.TernaryExpression;
import io.pebbletemplates.pebble.node.expression.UnaryExpression;
import io.pebbletemplates.pebble.operator.BinaryOperator;
import io.pebbletemplates.pebble.operator.UnaryOperator;

/**
 * Prints a parsed Pebble expression back as source that parses to the same tree, so two expressions print the same
 * text exactly when they are the same expression, whatever spacing or quotes they were written with.
 */
public final class PebbleExpressionPrinter {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final Map<Class<?>, String> binarySymbols;
    private final Map<Class<?>, String> unarySymbols;

    public PebbleExpressionPrinter(ExtensionRegistry registry) {
        this.binarySymbols = registry.getBinaryOperators().values().stream()
            .sorted(Comparator.comparing(BinaryOperator::getSymbol))
            .collect(Collectors.toMap(operator -> operator.createInstance().getClass(), BinaryOperator::getSymbol, (first, _) -> first));
        this.unarySymbols = registry.getUnaryOperators().values().stream()
            .sorted(Comparator.comparing(UnaryOperator::getSymbol))
            .collect(Collectors.toMap(UnaryOperator::getNodeClass, UnaryOperator::getSymbol, (first, _) -> first));
    }

    /**
     * @return empty when the expression holds a node that has no expression syntax, such as a block reference
     */
    public Optional<String> print(Expression<?> expression) {
        try {
            return Optional.of(text(expression));
        } catch (UnsupportedOperationException e) {
            return Optional.empty();
        }
    }

    /**
     * The expressions directly under this one, arguments included.
     */
    public static List<Expression<?>> children(Expression<?> expression) {
        return switch (expression) {
            case OverridableExpression overridable -> List.of(overridable.getExpression());
            case GetAttributeExpression attribute -> Stream.concat(
                Stream.of(attribute.getNode(), attribute.getAttributeNameExpression()),
                arguments(attribute.getArgumentsNode())
            ).toList();
            case FunctionOrMacroInvocationExpression function -> arguments(function.getArguments()).toList();
            case FilterInvocationExpression filter -> arguments(filter.getArgs()).toList();
            case TestInvocationExpression test -> arguments(test.getArgs()).toList();
            case BinaryExpression<?> binary -> List.of(binary.getLeftExpression(), binary.getRightExpression());
            case UnaryExpression unary when !(unary instanceof RenderableNodeExpression) -> List.of(unary.getChildExpression());
            case TernaryExpression ternary -> List.of(ternary.getExpression1(), ternary.getExpression2(), ternary.getExpression3());
            case ArrayExpression array -> List.copyOf(array.getValues());
            case MapExpression map -> map.getEntries().entrySet().stream().<Expression<?>>flatMap(entry -> Stream.of(entry.getKey(), entry.getValue())).toList();
            default -> List.of();
        };
    }

    private String text(Expression<?> expression) {
        return switch (expression) {
            case OverridableExpression overridable -> text(overridable.getExpression());
            case ContextVariableExpression variable -> variable.getName();
            case LiteralStringExpression string -> quote(string.getValue());
            case LiteralIntegerExpression number -> String.valueOf(number.getValue());
            case LiteralLongExpression number -> String.valueOf(number.getValue());
            case LiteralDoubleExpression number -> String.valueOf(number.getValue());
            case LiteralBigDecimalExpression number -> number.getValue().toPlainString();
            case LiteralBooleanExpression bool -> String.valueOf(bool.getValue());
            case LiteralNullExpression _ -> "null";
            case GetAttributeExpression attribute -> attribute(attribute);
            case FunctionOrMacroInvocationExpression function -> function.getFunctionName() + "(" + argumentList(function.getArguments()) + ")";
            case FilterInvocationExpression filter -> invocation(filter.getFilterName(), filter.getArgs());
            case TestInvocationExpression test -> invocation(test.getTestName(), test.getArgs());
            case FilterExpression filter -> (filter.getLeftExpression() instanceof FilterExpression left ? text(left) : operand(filter.getLeftExpression()))
                + " " + symbol(binarySymbols, filter) + " " + text(filter.getRightExpression());
            case BinaryExpression<?> binary -> operand(binary.getLeftExpression()) + " " + symbol(binarySymbols, binary) + " " + operand(binary.getRightExpression());
            case RenderableNodeExpression _ -> throw new UnsupportedOperationException();
            case UnaryExpression unary -> {
                String symbol = symbol(unarySymbols, unary);
                yield symbol + (IDENTIFIER.matcher(symbol).matches() ? " " : "") + operand(unary.getChildExpression());
            }
            case TernaryExpression ternary -> operand(ternary.getExpression1()) + " ? " + operand(ternary.getExpression2()) + " : " + operand(ternary.getExpression3());
            case ArrayExpression array -> array.getValues().stream().map(this::text).collect(Collectors.joining(", ", "[", "]"));
            case MapExpression map -> map.getEntries().entrySet().stream()
                .map(entry -> text(entry.getKey()) + ": " + text(entry.getValue()))
                .collect(Collectors.joining(", ", "{", "}"));
            default -> throw new UnsupportedOperationException();
        };
    }

    private String attribute(GetAttributeExpression attribute) {
        String node = operand(attribute.getNode());
        if (attribute.getAttributeNameExpression() instanceof LiteralStringExpression name && IDENTIFIER.matcher(name.getValue()).matches()) {
            String call = attribute.getArgumentsNode() == null ? "" : "(" + argumentList(attribute.getArgumentsNode()) + ")";
            return node + "." + name.getValue() + call;
        }
        return node + "[" + text(attribute.getAttributeNameExpression()) + "]";
    }

    private String invocation(String name, ArgumentsNode arguments) {
        String list = argumentList(arguments);
        return list.isEmpty() ? name : name + "(" + list + ")";
    }

    private String argumentList(ArgumentsNode arguments) {
        if (arguments == null) {
            return "";
        }
        return Stream.concat(
            ListUtils.emptyOnNull(arguments.getPositionalArgs()).stream().map(argument -> text(argument.getValueExpression())),
            ListUtils.emptyOnNull(arguments.getNamedArgs()).stream().map(argument -> argument.getName() + "=" + text(argument.getValueExpression()))
        ).collect(Collectors.joining(", "));
    }

    private String operand(Expression<?> expression) {
        Expression<?> unwrapped = expression instanceof OverridableExpression overridable ? overridable.getExpression() : expression;
        return unwrapped instanceof BinaryExpression<?> || unwrapped instanceof TernaryExpression ? "(" + text(unwrapped) + ")" : text(unwrapped);
    }

    private static String symbol(Map<Class<?>, String> symbols, Expression<?> expression) {
        String symbol = symbols.get(expression.getClass());
        if (symbol == null) {
            throw new UnsupportedOperationException();
        }
        return symbol;
    }

    private static String quote(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private static Stream<Expression<?>> arguments(ArgumentsNode arguments) {
        if (arguments == null) {
            return Stream.empty();
        }
        return Stream.concat(
            ListUtils.emptyOnNull(arguments.getPositionalArgs()).stream().map(argument -> argument.getValueExpression()),
            ListUtils.emptyOnNull(arguments.getNamedArgs()).stream().map(argument -> argument.getValueExpression())
        );
    }
}
