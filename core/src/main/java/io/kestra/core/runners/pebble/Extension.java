package io.kestra.core.runners.pebble;

import com.mitchellbosecke.pebble.extension.*;
import com.mitchellbosecke.pebble.node.expression.RangeExpression;
import com.mitchellbosecke.pebble.operator.Associativity;
import com.mitchellbosecke.pebble.operator.BinaryOperator;
import com.mitchellbosecke.pebble.operator.BinaryOperatorImpl;
import com.mitchellbosecke.pebble.operator.UnaryOperator;
import com.mitchellbosecke.pebble.tokenParser.TokenParser;
import io.kestra.core.runners.pebble.expression.NullCoalescingExpression;
import io.kestra.core.runners.pebble.filters.*;
import io.kestra.core.runners.pebble.functions.JsonFunction;
import io.kestra.core.runners.pebble.functions.NowFunction;
import io.kestra.core.runners.pebble.tests.JsonTest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;

import static com.mitchellbosecke.pebble.operator.BinaryOperatorType.NORMAL;

@Singleton
public class Extension extends AbstractExtension {
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
        filters.put("date", new DateFilter());
        filters.put("dateAdd", new DateAddFilter());
        filters.put("timestamp", new TimestampFilter());
        filters.put("timestampMicro", new TimestampMicroFilter());
        filters.put("timestampNano", new TimestampNanoFilter());
        filters.put("jq", new JqFilter());
        filters.put("json", new JsonFilter());
        filters.put("slugify", new SlugifyFilter());
        filters.put("substringBefore", new SubstringBeforeFilter());
        filters.put("substringBeforeLast", new SubstringBeforeLastFilter());
        filters.put("substringAfter", new SubstringAfterFilter());
        filters.put("substringAfterLast", new SubstringAfterLastFilter());

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
        Map<String, Function> tests = new HashMap<>();

        tests.put("now", new NowFunction());
        tests.put("json", new JsonFunction());

        return tests;
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


