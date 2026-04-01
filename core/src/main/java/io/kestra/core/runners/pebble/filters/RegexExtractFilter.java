package io.kestra.core.runners.pebble.filters;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pebble filter that extracts the first portion of a string matching a regular expression.
 *
 * <p>Usage: {@code {{ "order-12345-done" | regexExtract(regex="\d+") }}}</p>
 *
 * <p>An optional {@code group} argument (default {@code 0}) selects which capture group to return.
 * Group {@code 0} returns the entire match; groups {@code 1..n} return specific capture groups.</p>
 *
 * <p>Returns {@code null} if no match is found.</p>
 *
 * @see RegexMatchFilter
 * @see RegexReplaceFilter
 */
public class RegexExtractFilter implements Filter {

    public static final String FILTER_NAME = "regexExtract";

    private static final String ARGUMENT_REGEX = "regex";
    private static final String ARGUMENT_GROUP = "group";

    private static final List<String> ARGS = List.of(ARGUMENT_REGEX, ARGUMENT_GROUP);

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

        String regex = args.get(ARGUMENT_REGEX).toString();
        int group = args.containsKey(ARGUMENT_GROUP) && args.get(ARGUMENT_GROUP) != null
            ? ((Number) args.get(ARGUMENT_GROUP)).intValue()
            : 0;

        Matcher matcher = Pattern.compile(regex).matcher(input.toString());
        if (matcher.find()) {
            return matcher.group(group);
        }
        return null;
    }
}
