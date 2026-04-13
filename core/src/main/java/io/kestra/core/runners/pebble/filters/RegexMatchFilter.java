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
 * Pebble filter that checks if the input string contains a substring matching a given regular expression.
 *
 * <p>This filter uses {@code find()} semantics (partial match), not {@code matches()} (full-string match).
 * For example, {@code {{ "abc-123" | regexMatch(regex="\d+") }}} returns {@code true} because a
 * substring matches, even though the entire string does not.</p>
 *
 * <p>Usage: {@code {{ "hello world" | regexMatch(regex="hello.*") }}}</p>
 *
 * @see RegexReplaceFilter
 * @see RegexExtractFilter
 */
public class RegexMatchFilter implements Filter {

    public static final String NAME = "regexMatch";

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
        try {
            return RegexUtils.matcher(Pattern.compile(regex), input.toString()).find();
        } catch (PatternSyntaxException e) {
            throw new PebbleException(e, MessageFormat.format("Invalid regex ''{0}'': {1}", regex, e.getDescription()), lineNumber, self.getName());
        } catch (RegexUtils.RegexTimeoutException e) {
            throw new PebbleException(e, e.getMessage(), lineNumber, self.getName());
        }
    }
}
