package org.kestra.core.runners;

import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.helper.*;
import org.kestra.core.exceptions.IllegalVariableEvaluationException;
import org.kestra.core.runners.handlebars.helpers.InstantHelper;
import org.kestra.core.runners.handlebars.helpers.JsonHelper;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.kestra.core.utils.Rethrow.throwFunction;

public class VariableRenderer {
    private static final Handlebars handlebars = new Handlebars()
        .with(EscapingStrategy.NOOP)
        .registerHelpers(ConditionalHelpers.class)
        .registerHelpers(EachHelper.class)
        .registerHelpers(LogHelper.class)
        .registerHelpers(StringHelpers.class)
        .registerHelpers(UnlessHelper.class)
        .registerHelpers(WithHelper.class)
        .registerHelpers(InstantHelper.class)
        .registerHelpers(JsonHelper.class)
        .registerHelperMissing((context, options) -> {
            throw new IllegalStateException("Missing variable: " + options.helperName);
        });

    public String render(String inline, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        if (inline == null) {
            return null;
        }

        boolean isSame = false;
        String handlebarTemplate = inline;
        String current = "";
        Template template;


        while(!isSame) {
            try {
                template = handlebars.compileInline(handlebarTemplate);
                current = template.apply(variables);
            } catch (IOException e) {
                throw new IllegalVariableEvaluationException(e);
            }

            isSame = handlebarTemplate.equals(current);
            handlebarTemplate = current;
        }

        return current;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Map<String, Object> render(Map<String, Object> in, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        return in
            .entrySet()
            .stream()
            .map(throwFunction(r -> {
                Object value = r.getValue();

                if (r.getValue() instanceof Map) {
                    value = this.render((Map) r.getValue(), variables);
                } else if (r.getValue() instanceof Collection) {
                    value = this.render((List) r.getValue(), variables);
                } else if (r.getValue() instanceof String) {
                    value = this.render((String) r.getValue(), variables);
                }

                return new AbstractMap.SimpleEntry<>(
                    r.getKey(),
                    value
                );
            }))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a1, a2) -> a1));
    }

    public List<String> render(List<String> list, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        List<String> result = new ArrayList<>();

        for (String inline : list) {
            result.add(this.render(inline, variables));
        }

        return result;
    }
}
