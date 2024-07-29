package io.kestra.core.runners.pebble.functions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.serializers.JacksonMapper;
import io.pebbletemplates.pebble.error.PebbleException;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.List;
import java.util.Map;

public class FromIonFunction implements Function {
        private static final ObjectMapper MAPPER = JacksonMapper.ofIon();

        public List<String> getArgumentNames() {
            return List.of("ion");
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

            String ion = (String) args.get("ion");;

            try {
                return MAPPER.readValue(ion, JacksonMapper.OBJECT_TYPE_REFERENCE);
            } catch (JsonProcessingException e) {
                throw new PebbleException(null, "Invalid ion: " + e.getMessage(), lineNumber, self.getName());
            }
        }
}
