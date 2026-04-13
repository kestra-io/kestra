package io.kestra.core.runners.pebble;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kestra.core.runners.pebble.functions.KestraFunction;
import io.micronaut.context.ApplicationContext;
import io.pebbletemplates.pebble.extension.Extension;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.extension.core.CoreExtension;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class PebbleExpressionService {

    private final List<String> filters;
    private final List<PebbleFunction> functions;

    @Inject
    public PebbleExpressionService(ApplicationContext applicationContext) {
        // Start with the core Pebble extension, after customization (same as PebbleEngineFactory)
        ExtensionCustomizer customizedCore = new ExtensionCustomizer(new CoreExtension());
        Map<String, Filter> allFilters = new HashMap<>(customizedCore.getFilters());
        Map<String, Function> allFunctions = new HashMap<>(customizedCore.getFunctions());

        // Merge all registered Extension beans (includes Kestra's Extension + any plugin extensions)
        for (Extension ext : applicationContext.getBeansOfType(Extension.class)) {
            if (ext.getFilters() != null) {
                allFilters.putAll(ext.getFilters());
            }
            if (ext.getFunctions() != null) {
                allFunctions.putAll(ext.getFunctions());
            }
        }

        this.filters = allFilters.keySet().stream().sorted().toList();

        this.functions = allFunctions.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> {
                Function fn = entry.getValue();
                List<String> argNames = fn.getArgumentNames();
                if (argNames == null) {
                    return new PebbleFunction(entry.getKey(), List.of());
                }
                Map<String, String> defaults = fn instanceof KestraFunction kf ? kf.getArgumentDefaults() : Map.of();
                List<PebbleFunction.Argument> arguments = argNames.stream()
                    .map(name -> new PebbleFunction.Argument(name, defaults.get(name)))
                    .toList();
                return new PebbleFunction(entry.getKey(), arguments);
            })
            .toList();
    }

    public List<String> filters() {
        return filters;
    }

    public List<PebbleFunction> functions() {
        return functions;
    }
}
