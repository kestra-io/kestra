package io.kestra.core.runners.pebble.functions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.kestra.core.serializers.FileSerde;

import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import reactor.core.publisher.Flux;

public class FromIonFunction implements KestraFunction {
    public static final String NAME = "fromIon";
    public List<String> getArgumentNames() {
        return List.of("ion", "allRows");
    }

    @Override
    public Map<String, String> getArgumentDefaults() {
        HashMap<String, String> defaults = new HashMap<>();
        defaults.put("ion", ReadFileFunction.NAME + "('ion/namespace/file')");
        defaults.put("allRows", null);
        return defaults;
    }

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        if (!args.containsKey("ion")) {
            throw new PebbleException(null, "The 'fromIon' function expects an argument 'ion'.", lineNumber, self.getName());
        }

        Object ionArg = args.get("ion");
        if (ionArg == null) {
            return null;
        }

        byte[] ionBytes;
        if (ionArg instanceof byte[] bytes) {
            ionBytes = bytes;
        } else if (ionArg instanceof String str) {
            ionBytes = str.getBytes(StandardCharsets.UTF_8);
        } else {
            throw new PebbleException(null, "The 'fromIon' function expects an argument 'ion' with type string or byte[].", lineNumber, self.getName());
        }

        boolean allRows = args.containsKey("allRows") ? (Boolean) args.get("allRows") : false;

        try {
            Flux<Object> flux = FileSerde.readAll(new ByteArrayInputStream(ionBytes));

            if (!allRows) {
                flux = flux.take(1);
            }

            Stream<Object> data = flux.toStream();

            if (allRows) {
                return data.toList();
            }

            return data.findFirst().orElse(null);
        } catch (RuntimeException | IOException e) {
            throw new PebbleException(null, "Invalid ion: " + e.getMessage(), lineNumber, self.getName());
        }
    }
}
