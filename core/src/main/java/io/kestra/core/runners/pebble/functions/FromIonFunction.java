package io.kestra.core.runners.pebble.functions;

import io.kestra.core.serializers.FileSerde;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class FromIonFunction implements Function {
        public List<String> getArgumentNames() {
            return List.of("ion", "allRows");
        }

        @Override
        public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
            if (!args.containsKey("ion")) {
                throw new PebbleException(null, "The 'fromIon' function expects an argument 'ion'.", lineNumber, self.getName());
            }

            if (args.get("ion") == null) {
                return null;
            }

            if (!(args.get("ion") instanceof String)) {
                throw new PebbleException(null, "The 'fromIon' function expects an argument 'ion' with type string.", lineNumber, self.getName());
            }

            boolean allRows = args.containsKey("allRows") ? (Boolean) args.get("allRows") : false;

            try {
                String ion = (String) args.get("ion");;

                Flux<Object> flux = FileSerde.readAll(new BufferedReader(new StringReader(ion)));

                if (!allRows) {
                    flux = flux.take(1);
                }

                Stream<Object> data = flux
                    .toStream();

                if (allRows) {
                    return data.toList();
                }

                return data.findFirst().orElse(null);
            } catch (RuntimeException | IOException e) {
                throw new PebbleException(null, "Invalid ion: " + e.getMessage(), lineNumber, self.getName());
            }
        }
}
