package io.kestra.core.expression;

import io.kestra.core.models.annotations.Plugin;
import io.pebbletemplates.pebble.extension.Function;

/**
 * Class for defining a Pebble function.
 */
@Plugin
public abstract class PebbleFunction implements Function, PebbleExtension {

    /**
     * Returns the {@link Function} name.
     *
     * @return the string name  - cannot be {@link null}.
     */
    public String name() {
        String className = getClass().getSimpleName()
            .replace(PebbleFunction.class.getSimpleName(), "")
            .replace("Function", "");
        return className.substring(0, 1).toLowerCase() + className.substring(1);
    }
}
