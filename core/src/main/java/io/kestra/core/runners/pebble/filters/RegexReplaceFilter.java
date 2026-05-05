package io.kestra.core.runners.pebble.filters;

import io.kestra.core.utils.RegexUtils;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Pebble filter that replaces portions of a string matching a regular expression.
 *
 * <p>Usage: {@code {{ "hello world" | regexReplace(regex="world", replacement="java") }}}</p>
 *
 * <p>Capture groups can be referenced in the replacement string using {@code $1}, {@code $2}, etc.</p>
 *
 * @see RegexMatchFilter
 * @see RegexExtractFilter
 */
public class RegexReplaceFilter implements Filter {

    public static final String NAME = "regexReplace";

    private static final String ARGUMENT_REGEX = "regex";
    private static final String ARGUMENT_REPLACEMENT = "replacement";

    private static final List<String> ARGS = List.of(ARGUMENT_REGEX, ARGUMENT_REPLACEMENT);

    @Override
    public List<String> getArgumentNames() {
        return ARGS;
    }

    @Override
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        if (input == null) {
            return null;
        }

        if (args.get(ARGUMENT_REGEX) == null) {
            throw new PebbleException(
                null,
                MessageFormat.format("The argument ''{0}'' is required.", ARGUMENT_REGEX),
                lineNumber,
                self.getName()
            );
        }

        if (args.get(ARGUMENT_REPLACEMENT) == null) {
            throw new PebbleException(
                null,
                MessageFormat.format("The argument ''{0}'' is required.", ARGUMENT_REPLACEMENT),
                lineNumber,
                self.getName()
            );
        }

        String regex = args.get(ARGUMENT_REGEX).toString();
        String replacement = args.get(ARGUMENT_REPLACEMENT).toString();
        try {
            return RegexUtils.matcher(Pattern.compile(regex), input.toString()).replaceAll(replacement);
        } catch (PatternSyntaxException e) {
            throw new PebbleException(e, MessageFormat.format("Invalid regex ''{0}'': {1}", regex, e.getDescription()), lineNumber, self.getName());
        } catch (RegexUtils.RegexTimeoutException e) {
            throw new PebbleException(e, e.getMessage(), lineNumber, self.getName());
        }
    }
}
