package io.kestra.core.models.property;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageContext;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static io.kestra.core.utils.Rethrow.throwFunction;

@NoArgsConstructor
@JsonSerialize(using = DataProperty.DataPropertySerializer.class)
@JsonDeserialize(using = DataProperty.DataPropertyDeserializer.class)
public class DataProperty {
    private Object expression;
    private Data value;

    // used only by the deserializer
    DataProperty(Object expression) {
        this.expression = expression;
    }

    @SuppressWarnings({"unchecked", "raw"})
    public Data render(RunContext runContext) throws IllegalVariableEvaluationException {
        if (this.value == null) {
            if (this.expression instanceof String s) {
                String uri = runContext.render(s);
                if (!uri.startsWith(StorageContext.KESTRA_PROTOCOL)) {
                    throw new IllegalVariableEvaluationException("Not a valid Kestra internal storage URI.");
                }
                this.value = new Data(URI.create(uri));
            } else if (this.expression instanceof Map m) {
                Map<String, Object> value = runContext.render((Map<String, Object>) m);
                this.value = new Data(value);
            } else if (this.expression instanceof List l) {
                List<Map<String, Object>> values = l.stream()
                    .map(throwFunction(list -> runContext.render((Map<String, Object>) l)))
                    .toList();
                this.value = new Data(values);
            }

            throw new IllegalVariableEvaluationException("Not a valid data expression of type: " + this.expression.getClass().getSimpleName());
        }

        return value;
    }

    // used only by the serializer
    Object getExpression() {
        return this.expression;
    }

    @Override
    public String toString() {
        return value != null ? value.toString() : expression.toString();
    }

    public static class Data {
        private final URI uri;
        private final Map<String, Object> value;
        private final List<Map<String, Object>> values;

        Data(URI uri) {
            this.uri = uri;
            this.value = null;
            this.values = null;
        }

        Data(Map<String, Object> value) {
            this.uri = null;
            this.value = value;
            this.values = null;
        }

        Data(List<Map<String, Object>> values) {
            this.uri = null;
            this.value = null;
            this.values = values;
        }

        public boolean isUri() {
            return uri != null;
        }

        public void ifUri(Consumer<URI> consumer) {
            if (isUri()) {
                consumer.accept(this.uri);
            }
        }

        public boolean isValue() {
            return value != null;
        }

        public void ifValue(Consumer<Map<String, Object>> consumer) {
            if (isValue()) {
                consumer.accept(this.value);
            }
        }

        public boolean isValues() {
            return values != null;
        }

        public void ifValues(Consumer<List<Map<String, Object>>> consumer) {
            if (isValue()) {
                consumer.accept(this.values);
            }
        }

        public URI uri() {
            return uri;
        }

        public Map<String, Object> value() {
            return value;
        }

        public List<Map<String, Object>> values() {
            return values;
        }
    }

    static class DataPropertyDeserializer extends StdDeserializer<DataProperty> {
        private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAP_OF_STRING_OBJ = new TypeReference<>() {};

        protected DataPropertyDeserializer() {
            super(DataProperty.class);
        }

        @Override
        public DataProperty deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode jsonNode =  ctxt.readTree(p);
            Object obj;
            if (jsonNode.isNull()) {
                return null;
            } else if (jsonNode.isArray()) {
                ArrayNode array = (ArrayNode) jsonNode;
                Iterator<JsonNode> iterator = array.iterator();
                obj = StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 0), false)
                    .map(throwFunction(item -> ctxt.readTreeAsValue(item, ctxt.getTypeFactory().constructMapType(Map.class, String.class, String.class))))
                    .toList();
            } else if (jsonNode.isObject()) {
                obj = ctxt.readTreeAsValue(jsonNode, ctxt.getTypeFactory().constructMapType(Map.class, String.class, String.class));
            } else {
                obj = jsonNode.asText();
            }
            return new DataProperty(obj);
        }
    }

    static class DataPropertySerializer extends StdSerializer<DataProperty> {

        protected DataPropertySerializer() {
            super(DataProperty.class);
        }

        @Override
        public void serialize(DataProperty value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            if (value.getExpression() instanceof String s) {
                gen.writeString(s);
            } else if (value.getExpression() instanceof List list) {
                gen.writeStartArray();
                list.forEach(throwConsumer(obj -> {
                    gen.writeStartObject();
                    gen.writeObject(obj);
                    gen.writeEndObject();
                }));
                gen.writeEndArray();
            } else {
                gen.writeStartObject();
                gen.writeObject(value.getExpression());
                gen.writeEndObject();
            }
        }
    }
}
