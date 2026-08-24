package io.kestra.core.runners.pebble.functions;

import java.util.List;
import java.util.Map;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.extension.core.RangeFunction;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

/**
 * Wraps Pebble's built-in {@code range()} function with an upper bound on the number of
 * elements it can produce. Pebble's {@link RangeFunction} eagerly materializes the whole
 * range into an {@code ArrayList} before returning, so an expression such as
 * {@code range(0, 2000000000)} allocates billions of entries and can exhaust the JVM heap.
 * This wrapper computes the requested size upfront and fails with a bounded {@link PebbleException}
 * instead of delegating once that size is exceeded.
 */
public final class BoundedRangeFunction implements Function {
    public static final String NAME = RangeFunction.FUNCTION_NAME;

    private static final long MAX_RANGE_SIZE = 1_000_000L;

    private static final String PARAM_START = "start";
    private static final String PARAM_END = "end";
    private static final String PARAM_INCREMENT = "increment";

    private final RangeFunction delegate = new RangeFunction();

    @Override
    public List<String> getArgumentNames() {
        return delegate.getArgumentNames();
    }

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        Object start = args.get(PARAM_START);
        Object end = args.get(PARAM_END);
        Object increment = args.get(PARAM_INCREMENT);

        if (start instanceof Number startNumber && end instanceof Number endNumber) {
            long incrementValue = increment instanceof Number number ? number.longValue() : 1L;
            if (incrementValue != 0) {
                long requestedSize = Math.abs(endNumber.longValue() - startNumber.longValue()) / Math.abs(incrementValue) + 1;
                if (requestedSize > MAX_RANGE_SIZE) {
                    throw new PebbleException(
                        null,
                        "The range function cannot produce more than %d elements, but this call would produce %d.".formatted(MAX_RANGE_SIZE, requestedSize),
                        lineNumber,
                        self.getName()
                    );
                }
            }
        }

        return delegate.execute(args, self, context, lineNumber);
    }
}
