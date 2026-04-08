package io.kestra.core.runners.pebble;

import java.util.Map;

import io.kestra.core.runners.pebble.functions.TestDeprecatedFunction;
import io.pebbletemplates.pebble.extension.AbstractExtension;
import io.pebbletemplates.pebble.extension.Function;
import jakarta.inject.Singleton;

@Singleton
public class TestPebbleExtension extends AbstractExtension {
    @Override
    public Map<String, Function> getFunctions() {
        return Map.of(TestDeprecatedFunction.NAME, new TestDeprecatedFunction());
    }
}
