package io.kestra.core.runners.pebble.filters;

import com.mitchellbosecke.pebble.error.PebbleException;
import com.mitchellbosecke.pebble.extension.Filter;
import com.mitchellbosecke.pebble.template.EvaluationContext;
import com.mitchellbosecke.pebble.template.PebbleTemplate;
import io.kestra.core.runners.pebble.AbstractDate;
import io.kestra.core.utils.Slugify;

import java.util.Map;

public class SlugifyFilter extends AbstractDate implements Filter {
    @Override
    public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) throws PebbleException {
        if (input == null) {
            return null;
        }

        return Slugify.of(input.toString());
    }
}
