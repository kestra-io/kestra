package io.kestra.core.runners;

import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.helper.*;
import io.micronaut.context.ApplicationContext;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.handlebars.helpers.*;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Singleton
public class VariableRenderer {
    private final Handlebars handlebars;

    @SuppressWarnings("unchecked")
    @Inject
    public VariableRenderer(ApplicationContext applicationContext) {
        this.handlebars = new Handlebars()
            .with(EscapingStrategy.NOOP)
            .registerHelpers(ConditionalHelpers.class)
            .registerHelpers(EachHelper.class)
            .registerHelpers(LogHelper.class)
            .registerHelpers(StringHelpers.class)
            .registerHelpers(OtherStringsHelper.class)
            .registerHelpers(UnlessHelper.class)
            .registerHelpers(WithHelper.class)
            .registerHelpers(DateHelper.class)
            .registerHelpers(JsonHelper.class)
            .registerHelpers(MiscHelper.class)
            .registerHelper("eval", new EvalHelper(this))
            .registerHelper("firstDefinedEval", new FirstDefinedEvalHelper(this))
            .registerHelper("jq", new JqHelper())
            .registerHelperMissing((context, options) -> {
                throw new IllegalStateException("Missing variable: " + options.helperName);
            });

        applicationContext.getBeansOfType(VariableRendererPlugins.class)
            .forEach(variableRendererPlugins -> {
                this.handlebars.registerHelper(
                    variableRendererPlugins.name(),
                    variableRendererPlugins.helper()
                );
            });
    }

    public String recursiveRender(String inline, Object variables) throws IllegalVariableEvaluationException {
        if (inline == null) {
            return null;
        }

        boolean isSame = false;
        String handlebarTemplate = inline;
        String current = "";
        Template template;

        while (!isSame) {
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

    public String render(String inline, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        return this.recursiveRender(inline, variables);
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
            result.add(this.recursiveRender(inline, variables));
        }

        return result;
    }
}
