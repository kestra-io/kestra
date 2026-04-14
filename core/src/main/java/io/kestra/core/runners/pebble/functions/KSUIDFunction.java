package io.kestra.core.runners.pebble.functions;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

import com.github.ksuid.KsuidGenerator;

import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

/**
 * This function implements the 'ksuid' function which generates a K-Sortable Unique IDentifier.
 * KSUID is a 20-byte identifier: 4 bytes of timestamp + 16 random bytes, encoded as base62.
 *
 * @see <a href="https://github.com/segmentio/ksuid">https://github.com/segmentio/ksuid</a>
 * @see <a href="https://github.com/ksuid/ksuid">https://github.com/ksuid/ksuid</a>
 */
public class KSUIDFunction implements KestraFunction {
    public static final String NAME = "ksuid";
    private static final KsuidGenerator KSUID_GENERATOR = new KsuidGenerator(new SecureRandom());

    @Override
    public Object execute(
        Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        return generateKsuid();
    }

    @Override
    public List<String> getArgumentNames() {
        return List.of();
    }

    @Override
    public Map<String, String> getArgumentDefaults() {
        return Map.of();
    }

    private String generateKsuid() {
        return KSUID_GENERATOR.newKsuid().toString();
    }
}
