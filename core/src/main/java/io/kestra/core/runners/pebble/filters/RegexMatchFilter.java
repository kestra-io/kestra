package io.kestra.core.runners.pebble.filters;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Pebble filter that checks if the input string matches a given regular expression.
 *
 * <p>Usage: {@code {{ "hello world" | regexMatch(regex="hello.*") }}}</p>
 *
 * @see RegexReplaceFilter
 * @see RegexExtractFilter
 */
public class RegexMatchFilter implements Filter {

    public static final String FILTER_NAME = "regexMatch";

    private static final String ARGUMENT_REGEX = "regex";

    private static final List<String> ARGS = List.of(ARGUMENT_REGEX);

    @Override
    public List<String> getArgumentNames() {
        return ARGS;
    }

    @Override
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        if (input == null) {
            return false;
        }

        if (args.get(ARGUMENT_REGEX) == null) {
            throw new PebbleException(
                null,
                MessageFormat.format("The argument ''{0}'' is required.", ARGUMENT_REGEX),
                lineNumber,
                self.getName()
            );
        }

        String regex = args.get(ARGUMENT_REGEX).toString();
        return Pattern.compile(regex).matcher(input.toString()).find();
    }
}
