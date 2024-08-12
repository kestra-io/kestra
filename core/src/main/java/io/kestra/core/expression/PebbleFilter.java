package io.kestra.core.expression;

import io.kestra.core.models.annotations.Plugin;
import io.pebbletemplates.pebble.extension.Filter;

/**
 * Class for defining a Pebble filter.
 */
@Plugin
public abstract class PebbleFilter implements Filter, PebbleExtension {

    /**
     * Returns the {@link Filter} name.
     * 
     * @return the string name  - cannot be {@link null}.
     */
    public String name() {
        String className = getClass().getSimpleName()
            .replace(PebbleFilter.class.getSimpleName(), "")
            .replace("Filter", "");
        return className.substring(0, 1).toLowerCase() + className.substring(1);
    }
}
