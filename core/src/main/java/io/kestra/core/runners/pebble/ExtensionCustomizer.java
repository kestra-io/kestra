package io.kestra.core.runners.pebble;

import com.mitchellbosecke.pebble.attributes.AttributeResolver;
import com.mitchellbosecke.pebble.extension.*;
import com.mitchellbosecke.pebble.extension.Extension;
import com.mitchellbosecke.pebble.extension.core.MacroAndBlockRegistrantNodeVisitorFactory;
import com.mitchellbosecke.pebble.extension.core.NumberFormatFilter;
import com.mitchellbosecke.pebble.operator.BinaryOperator;
import com.mitchellbosecke.pebble.operator.UnaryOperator;
import com.mitchellbosecke.pebble.tokenParser.*;

import java.util.*;

public class ExtensionCustomizer extends com.mitchellbosecke.pebble.extension.ExtensionCustomizer {
    public ExtensionCustomizer(Extension ext) {
        super(ext);
    }

    @Override
    public Map<String, Filter> getFilters() {
        Map<String, Filter> list = Optional
            .ofNullable(super.getFilters())
            .map(HashMap::new)
            .orElse(new HashMap<>());

        list.remove("escape");
        list.remove("raw");

        list.put("numberFormat", new NumberFormatFilter());
        list.remove("numberformat");

        return list;
    }

    @Override
    public Map<String, Test> getTests() {
        return super.getTests();
    }

    @Override
    public Map<String, Function> getFunctions() {
        Map<String, Function> list = Optional
            .ofNullable(super.getFunctions())
            .map(HashMap::new)
            .orElse(new HashMap<>());

        list.remove("l18n");

        return list;
    }

    @Override
    public List<TokenParser> getTokenParsers() {
        List<TokenParser> list = Optional
            .ofNullable(super.getTokenParsers())
            .map(ArrayList::new)
            .orElseGet(ArrayList::new);

        list.removeIf(x -> x instanceof AutoEscapeTokenParser);
        list.removeIf(x -> x instanceof ExtendsTokenParser);
        list.removeIf(x -> x instanceof EmbedTokenParser);
        list.removeIf(x -> x instanceof FlushTokenParser);
        list.removeIf(x -> x instanceof ImportTokenParser);
        list.removeIf(x -> x instanceof IncludeTokenParser);
        list.removeIf(x -> x instanceof ParallelTokenParser);
        list.removeIf(x -> x instanceof CacheTokenParser);
        list.removeIf(x -> x instanceof FromTokenParser);
        list.removeIf(x -> x instanceof AutoEscapeTokenParser);
        list.removeIf(x -> x instanceof AutoEscapeTokenParser);
        list.removeIf(x -> x instanceof AutoEscapeTokenParser);
        list.removeIf(x -> x instanceof AutoEscapeTokenParser);
        list.removeIf(x -> x instanceof AutoEscapeTokenParser);

        return list;
    }

    @Override
    public List<BinaryOperator> getBinaryOperators() {
        return super.getBinaryOperators();
    }

    @Override
    public List<UnaryOperator> getUnaryOperators() {
        return super.getUnaryOperators();
    }

    @Override
    public Map<String, Object> getGlobalVariables() {
        return super.getGlobalVariables();
    }

    @Override
    public List<NodeVisitorFactory> getNodeVisitors() {
        List<NodeVisitorFactory> list = Optional
            .ofNullable(super.getNodeVisitors())
            .map(ArrayList::new)
            .orElseGet(ArrayList::new);

        return list;
    }

    @Override
    public List<AttributeResolver> getAttributeResolver() {
        return super.getAttributeResolver();
    }
}