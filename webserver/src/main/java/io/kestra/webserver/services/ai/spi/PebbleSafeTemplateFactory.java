package io.kestra.webserver.services.ai.spi;

import dev.langchain4j.spi.prompt.PromptTemplateFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

/**
 * Custom TemplateFactory that creates a lenient template that allows the template to contain a variable that is not passed to the template.
 * This is because we use <code>{{variable}}</code> in our doc and blueprints for Pebble variables, so when a prompt contains that, it
 * didn't always means it's coming from a prompt variable, but can come from some content retrieved (RAG for ex).
 */
public class PebbleSafeTemplateFactory implements PromptTemplateFactory {
    @Override
    public Template create(Input input) {
        return new PebbleSafeTemplate(input.getTemplate());
    }

    // This is more or less the same as the DefaultTemplate but with {_{var}} instead of {{var}} to use variables to avoid conflicting with Pebble.
    static class PebbleSafeTemplate implements Template {

        /**
         * A regular expression pattern for identifying variable placeholders within double curly braces in a template string.
         * Variables are denoted as <code>{{variable_name}}</code> or <code>{{ variable_name }}</code>,
         * where spaces around the variable name are allowed.
         * <p>
         * This pattern is used to match and extract variables from a template string for further processing,
         * such as replacing these placeholders with their corresponding values.
         */
        @SuppressWarnings({"RegExpRedundantEscape"})
        private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{_\\{\\s*(.+?)\\s*}_}");

        private final String template;

        public PebbleSafeTemplate(String template) {
            this.template = ensureNotBlank(template, "template");
        }

        private static Set<String> extractVariables(String template) {
            Set<String> variables = new HashSet<>();
            Matcher matcher = VARIABLE_PATTERN.matcher(template);
            while (matcher.find()) {
                variables.add(matcher.group(1));
            }
            return variables;
        }

        public String render(Map<String, Object> variables) {
            String result = template;
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                result = replaceAll(result, entry.getKey(), entry.getValue());
            }

            return result;
        }

        private static String replaceAll(String template, String variable, Object value) {
            if (value == null || value.toString() == null) {
                throw illegalArgument("Value for the variable '%s' is null", variable);
            }
            return template.replace(inAiVarBrackets(variable), value.toString());
        }

        private static String inAiVarBrackets(String variable) {
            return "{_{" + variable + "}_}";
        }
    }
}
