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
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.RunContext;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@NoArgsConstructor
@JsonSerialize(using = ArrayProperty.ArrayPropertySerializer.class)
@JsonDeserialize(using = ArrayProperty.ArrayPropertyDeserializer.class)
public class ArrayProperty {
    private Object expression;
    private Data value;

    // used only by the deserializer
    ArrayProperty(Object expression) {
        this.expression = expression;
    }

    @SuppressWarnings({"unchecked", "raw"})
    public Data render(RunContext runContext) throws IllegalVariableEvaluationException {
        if (this.value == null) {
            if (this.expression instanceof String s) {
                String value = runContext.render(s);
                this.value = new Data(value);
            } else if (this.expression instanceof List l) {
                List<String> values = runContext.render(l);
                this.value = new Data(values);
            } else {
                throw new IllegalVariableEvaluationException("Not a valid data expression of type: " + this.expression.getClass().getName());
            }
        }

        return this.value;
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
        private final String value;
        private final List<String> values;

        Data(String value) {
            this.value = value;
            this.values = null;
        }

        Data(List<String> values) {
            this.value = null;
            this.values = values;
        }

        public boolean isValue() {
            return value != null;
        }

        public void ifValue(Consumer<String> consumer) {
            if (isValue()) {
                consumer.accept(this.value);
            }
        }

        public boolean isValues() {
            return values != null;
        }

        public void ifValues(Consumer<List<String>> consumer) {
            if (isValues()) {
                consumer.accept(this.values);
            }
        }

        public String value() {
            return value;
        }

        public List<String> values() {
            return values;
        }
    }

    static class ArrayPropertyDeserializer extends StdDeserializer<ArrayProperty> {
        private static final TypeReference<List<String>> LIST_OF_STRING = new TypeReference<>() {};

        protected ArrayPropertyDeserializer() {
            super(ArrayProperty.class);
        }

        @Override
        public ArrayProperty deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode jsonNode =  ctxt.readTree(p);
            Object obj;
            if (jsonNode.isNull()) {
                return null;
            } else if (jsonNode.isArray()) {
                ArrayNode array = (ArrayNode) jsonNode;
                Iterator<JsonNode> iterator = array.iterator();
                obj = StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 0), false)
                    .map(item -> item.asText())
                    .toList();
            } else {
                obj = p.getValueAsString();
            }
            return new ArrayProperty(obj);
        }
    }

    static class ArrayPropertySerializer extends StdSerializer<ArrayProperty> {

        protected ArrayPropertySerializer() {
            super(ArrayProperty.class);
        }

        @Override
        @SuppressWarnings({"raw", "unchecked"})
        public void serialize(ArrayProperty value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            if (value.getExpression() == null) {
                gen.writeNull();
            } else if (value.getExpression() instanceof List list) {
                gen.writeStartArray();
                gen.writeArray((String[]) list.toArray(new String[] {}), 0, list.size());
                gen.writeEndArray();
            } else if (value.getExpression() instanceof String s) {
                gen.writeString(s);
            } else {
                throw new IllegalArgumentException("Not a valid array expression of type: " + value.getExpression().getClass().getName());
            }
        }
    }
}
