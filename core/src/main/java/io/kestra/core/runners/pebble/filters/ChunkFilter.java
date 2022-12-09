package io.kestra.core.runners.pebble.filters;

import com.google.common.collect.Lists;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.List;
import java.util.Map;

public class ChunkFilter implements Filter {
    @Override
    public List<String> getArgumentNames() {
        return List.of("size");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        if (input == null) {
            return null;
        }

        if (!args.containsKey("size")) {
            throw new PebbleException(null, "'chunk' filter expects an argument 'size'.", lineNumber, self.getName());
        }

        if (!(input instanceof List)) {
            throw new PebbleException(null, "'chunk' filter can only be applied to List. Actual type was: " + input.getClass().getName(), lineNumber, self.getName());
        }

        return Lists.partition((List) input, ((Long) args.get("size")).intValue());
    }
}
