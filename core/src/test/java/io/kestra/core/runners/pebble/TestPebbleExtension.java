package io.kestra.core.runners.pebble;

import java.util.Map;

import io.kestra.core.runners.pebble.functions.DeprecatedTestFunction;

import io.pebbletemplates.pebble.extension.AbstractExtension;
import io.pebbletemplates.pebble.extension.Function;
import jakarta.inject.Singleton;

/**
 * Test-only Pebble extension that registers {@link DeprecatedTestFunction}
 * so {@link PebbleExpressionService} can discover it in {@code @KestraTest} contexts.
 */
@SuppressWarnings("deprecation")
@Singleton
public class TestPebbleExtension extends AbstractExtension {

    @Override
    public Map<String, Function> getFunctions() {
        return Map.of(DeprecatedTestFunction.NAME, new DeprecatedTestFunction());
    }
}
