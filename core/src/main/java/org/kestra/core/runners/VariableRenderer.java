package org.kestra.core.runners;

import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.helper.*;
import org.kestra.core.exceptions.IllegalVariableEvaluationException;
import org.kestra.core.runners.handlebars.helpers.InstantHelper;
import org.kestra.core.runners.handlebars.helpers.JsonHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VariableRenderer {
    private static Handlebars handlebars = new Handlebars()
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

    public List<String> render(List<String> list, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        List<String> result = new ArrayList<>();

        for (String inline : list) {
            result.add(this.render(inline, variables));
        }

        return result;
    }
}
