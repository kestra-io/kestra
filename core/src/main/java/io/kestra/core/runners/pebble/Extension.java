package io.kestra.core.runners.pebble;

import io.kestra.core.runners.pebble.functions.*;
import io.micronaut.core.annotation.Nullable;
import io.pebbletemplates.pebble.extension.*;
import io.pebbletemplates.pebble.operator.Associativity;
import io.pebbletemplates.pebble.operator.BinaryOperator;
import io.pebbletemplates.pebble.operator.BinaryOperatorImpl;
import io.pebbletemplates.pebble.operator.UnaryOperator;
import io.pebbletemplates.pebble.tokenParser.TokenParser;
import io.kestra.core.runners.pebble.expression.NullCoalescingExpression;
import io.kestra.core.runners.pebble.filters.*;
import io.kestra.core.runners.pebble.tests.JsonTest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static io.pebbletemplates.pebble.operator.BinaryOperatorType.NORMAL;

@Singleton
public class Extension extends AbstractExtension {
    @Inject
    private SecretFunction secretFunction;

    @Inject
    private ReadFileFunction readFileFunction;

    @Inject
    @Nullable
    private RenderFunction renderFunction;

    @Override
    public List<TokenParser> getTokenParsers() {
        return null;
    }

    @Override
    public List<UnaryOperator> getUnaryOperators() {
        return null;
    }

    @Override
    public List<BinaryOperator> getBinaryOperators() {
        List<BinaryOperator> operators = new ArrayList<>();

        operators.add(new BinaryOperatorImpl("??", 120, NullCoalescingExpression::new, NORMAL, Associativity.LEFT));

        return operators;
    }

    @Override
    public Map<String, Filter> getFilters() {
        Map<String, Filter> filters = new HashMap<>();

        filters.put("chunk", new ChunkFilter());
        filters.put("className", new ClassNameFilter());
        filters.put("date", new DateFilter());
        filters.put("dateAdd", new DateAddFilter());
        filters.put("timestamp", new TimestampFilter());
        filters.put("timestampMicro", new TimestampMicroFilter());
        filters.put("timestampNano", new TimestampNanoFilter());
        filters.put("jq", new JqFilter());
        filters.put("json", new JsonFilter());
        filters.put("yaml", new JsonFilter());
        filters.put("keys", new KeysFilter());
        filters.put("number", new NumberFilter());
        filters.put("urldecode", new UrlDecoderFilter());
        filters.put("slugify", new SlugifyFilter());
        filters.put("substringBefore", new SubstringBeforeFilter());
        filters.put("substringBeforeLast", new SubstringBeforeLastFilter());
        filters.put("substringAfter", new SubstringAfterFilter());
        filters.put("substringAfterLast", new SubstringAfterLastFilter());
        filters.put("flatten",new FlattenFilter());
        return filters;
    }

    @Override
    public Map<String, Test> getTests() {
        Map<String, Test> tests = new HashMap<>();

        tests.put("json", new JsonTest());

        return tests;
    }

    @Override
    public Map<String, Function> getFunctions() {
        Map<String, Function> functions = new HashMap<>();

        functions.put("now", new NowFunction());
        functions.put("json", new JsonFunction());
        functions.put("yaml", new YamlFunction());
        functions.put("currentEachOutput", new CurrentEachOutputFunction());
        functions.put("secret", secretFunction);
        functions.put("read", readFileFunction);
        if (this.renderFunction != null) {
            functions.put("render", renderFunction);
        }
        functions.put("encrypt", new EncryptFunction());
        functions.put("decrypt", new DecryptFunction());

        return functions;
    }

    @Override
    public Map<String, Object> getGlobalVariables() {
        return null;
    }

    @Override
    public List<NodeVisitorFactory> getNodeVisitors() {
        return null;
    }
}


