package io.kestra.core.models.property;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.io.Serial;
import java.util.List;
import java.util.Map;

/**
 * Define a plugin properties that will be rendered and converted to a target type at use time.
 *
 * @param <T> the target type of the property
 */
@JsonDeserialize(using = Property.PropertyDeserializer.class)
@JsonSerialize(using = Property.PropertySerializer.class)
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class Property<T> {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    private String expression;
    private T value;

    // used only by the deserializer and in tests
    @VisibleForTesting
    public Property(String expression) {
        this.expression = expression;
    }

    /**
     * Build a new Property object with a value already set.<br>
     *
     * A property build with this method will always return the value passed at build time, no rendering will be done.
     */
    public static <V> Property<V> of(V value) {
        // trick the serializer so the property would not be null at deserialization time
        String expression;
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            try {
                expression = MAPPER.writeValueAsString(value);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException(e);
            }
        } else {
            expression = MAPPER.convertValue(value, String.class);
        }
        Property<V> p = new Property<>(expression);
        p.value = value;
        return p;
    }

    /**
     * Render a property then convert it to its target type.<br>
     *
     * This method is designed to be used only by the {@link io.kestra.core.runners.RunContextProperty}.
     *
     * @see io.kestra.core.runners.RunContextProperty#as(Class)
     */
    public static <T> T as(Property<T> property, RunContext runContext, Class<T> clazz) throws IllegalVariableEvaluationException {
        if (property.value == null) {
            String rendered =  runContext.render(property.expression);
            property.value = MAPPER.convertValue(rendered, clazz);
        }

        return property.value;
    }

    /**
     * Render a property with additional variables, then convert it to its target type.<br>
     *
     * This method is designed to be used only by the {@link io.kestra.core.runners.RunContextProperty}.
     *
     * @see io.kestra.core.runners.RunContextProperty#as(Class, Map)
     */
    public static <T> T as(Property<T> property, RunContext runContext, Class<T> clazz, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        if (property.value == null) {
            String rendered =  runContext.render(property.expression, variables);
            property.value = MAPPER.convertValue(rendered, clazz);
        }

        return property.value;
    }

    /**
     * Render a property then convert it as a list of target type.<br>
     *
     * This method is designed to be used only by the {@link io.kestra.core.runners.RunContextProperty}.
     *
     * @see io.kestra.core.runners.RunContextProperty#asList(Class)
     */
    public static <T, I> T asList(Property<T> property, RunContext runContext, Class<I> itemClazz) throws IllegalVariableEvaluationException {
        if (property.value == null) {
            String rendered =  runContext.render(property.expression);
            JavaType type = MAPPER.getTypeFactory().constructCollectionLikeType(List.class, itemClazz);
            try {
                property.value = MAPPER.readValue(rendered, type);
            } catch (JsonProcessingException e) {
                throw new IllegalVariableEvaluationException(e);
            }
        }

        return property.value;
    }

    /**
     * Render a property with additional variables, then convert it as a list of target type.<br>
     *
     * This method is designed to be used only by the {@link io.kestra.core.runners.RunContextProperty}.
     *
     * @see io.kestra.core.runners.RunContextProperty#asList(Class, Map)
     */
    public static <T, I> T asList(Property<T> property, RunContext runContext, Class<I> itemClazz, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        if (property.value == null) {
            String rendered =  runContext.render(property.expression, variables);
            JavaType type = MAPPER.getTypeFactory().constructCollectionLikeType(List.class, itemClazz);
            try {
                property.value = MAPPER.readValue(rendered, type);
            } catch (JsonProcessingException e) {
                throw new IllegalVariableEvaluationException(e);
            }
        }

        return property.value;
    }

    /**
     * Render a property then convert it as a map of target types.<br>
     *
     * This method is designed to be used only by the {@link io.kestra.core.runners.RunContextProperty}.
     *
     * @see io.kestra.core.runners.RunContextProperty#asMap(Class, Class)
     */
    public static <T, K,V> T asMap(Property<T> property, RunContext runContext, Class<K> keyClass, Class<V> valueClass) throws IllegalVariableEvaluationException {
        if (property.value == null) {
            String rendered =  runContext.render(property.expression);
            JavaType type = MAPPER.getTypeFactory().constructMapType(Map.class, keyClass, valueClass);
            try {
                property.value = MAPPER.readValue(rendered, type);
            } catch (JsonProcessingException e) {
                throw new IllegalVariableEvaluationException(e);
            }
        }

        return property.value;
    }

    /**
     * Render a property with additional variables, then convert it as a map of target types.<br>
     *
     * This method is safe to be used as many times as you want as the rendering and conversion will be cached.
     * Warning, due to the caching mechanism, this method is not thread-safe.
     *
     * @see io.kestra.core.runners.RunContextProperty#asMap(Class, Class, Map)
     */
    public static <T, K,V> T asMap(Property<T> property, RunContext runContext, Class<K> keyClass, Class<V> valueClass, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        if (property.value == null) {
            String rendered =  runContext.render(property.expression, variables);
            JavaType type = MAPPER.getTypeFactory().constructMapType(Map.class, keyClass, valueClass);
            try {
                property.value = MAPPER.readValue(rendered, type);
            } catch (JsonProcessingException e) {
                throw new IllegalVariableEvaluationException(e);
            }
        }

        return property.value;
    }

    /**
     * Render a property then convert it to its target type.<br>
     *
     * This method is safe to be used as many times as you want as the rendering and conversion will be cached.
     * Warning, due to the caching mechanism, this method is not thread-safe.
     *
     * @deprecated use RunContext.render(Property) instead.
     */
    @Deprecated(forRemoval = true)
    public T as(RunContext runContext, Class<T> clazz) throws IllegalVariableEvaluationException {
        if (this.value == null) {
            String rendered =  runContext.render(expression);
            this.value = MAPPER.convertValue(rendered, clazz);
        }

        return this.value;
    }

    /**
     * Render a property with additional variables, then convert it to its target type.<br>
     *
     * This method is safe to be used as many times as you want as the rendering and conversion will be cached.
     * Warning, due to the caching mechanism, this method is not thread-safe.
     *
     * @deprecated use RunContext.render(Property) instead.
     */
    @Deprecated(forRemoval = true)
    public T as(RunContext runContext, Class<T> clazz, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        if (this.value == null) {
            String rendered =  runContext.render(expression, variables);
            this.value = MAPPER.convertValue(rendered, clazz);
        }

        return this.value;
    }

    /**
     * Render a property then convert it as a list of target type.<br>
     *
     * This method is safe to be used as many times as you want as the rendering and conversion will be cached.
     * Warning, due to the caching mechanism, this method is not thread-safe.
     *
     * @deprecated use RunContext.render(Property) instead.
     */
    @Deprecated(forRemoval = true)
    public <I> T asList(RunContext runContext, Class<I> itemClazz) throws IllegalVariableEvaluationException {
        if (this.value == null) {
            String rendered =  runContext.render(expression);
            JavaType type = MAPPER.getTypeFactory().constructCollectionLikeType(List.class, itemClazz);
            try {
                this.value = MAPPER.readValue(rendered, type);
            } catch (JsonProcessingException e) {
                throw new IllegalVariableEvaluationException(e);
            }
        }

        return this.value;
    }

    /**
     * Render a property with additional variables, then convert it as a list of target type.<br>
     *
     * This method is safe to be used as many times as you want as the rendering and conversion will be cached.
     * Warning, due to the caching mechanism, this method is not thread-safe.
     *
     * @deprecated use RunContext.render(Property) instead.
     */
    @Deprecated(forRemoval = true)
    public <I> T asList(RunContext runContext, Class<I> itemClazz, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        if (this.value == null) {
            String rendered =  runContext.render(expression, variables);
            JavaType type = MAPPER.getTypeFactory().constructCollectionLikeType(List.class, itemClazz);
            try {
                this.value = MAPPER.readValue(rendered, type);
            } catch (JsonProcessingException e) {
                throw new IllegalVariableEvaluationException(e);
            }
        }

        return this.value;
    }

    /**
     * Render a property then convert it as a map of target types.<br>
     *
     * This method is safe to be used as many times as you want as the rendering and conversion will be cached.
     * Warning, due to the caching mechanism, this method is not thread-safe.
     *
     * @deprecated use RunContext.render(Property) instead.
     */
    @Deprecated(forRemoval = true)
    public <K,V> T asMap(RunContext runContext, Class<K> keyClass, Class<V> valueClass) throws IllegalVariableEvaluationException {
        if (this.value == null) {
            String rendered =  runContext.render(expression);
            JavaType type = MAPPER.getTypeFactory().constructMapType(Map.class, keyClass, valueClass);
            try {
                this.value = MAPPER.readValue(rendered, type);
            } catch (JsonProcessingException e) {
                throw new IllegalVariableEvaluationException(e);
            }
        }

        return this.value;
    }

    /**
     * Render a property with additional variables, then convert it as a map of target types.<br>
     *
     * This method is safe to be used as many times as you want as the rendering and conversion will be cached.
     * Warning, due to the caching mechanism, this method is not thread-safe.
     *
     * @deprecated use RunContext.render(Property) instead.
     */
    @Deprecated(forRemoval = true)
    public <K,V> T asMap(RunContext runContext, Class<K> keyClass, Class<V> valueClass, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        if (this.value == null) {
            String rendered =  runContext.render(expression, variables);
            JavaType type = MAPPER.getTypeFactory().constructMapType(Map.class, keyClass, valueClass);
            try {
                this.value = MAPPER.readValue(rendered, type);
            } catch (JsonProcessingException e) {
                throw new IllegalVariableEvaluationException(e);
            }
        }

        return this.value;
    }

    @Override
    public String toString() {
        return value != null ? value.toString() : expression;
    }

    // used only by the serializer
    String getExpression() {
        return this.expression;
    }

    static class PropertyDeserializer extends StdDeserializer<Property<?>> {
        @Serial
        private static final long serialVersionUID = 1L;

        protected PropertyDeserializer() {
            super(Property.class);
        }

        @Override
        public Property<?> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String s;
            if (p.isExpectedStartArrayToken()) {
                List<Object> list = p.readValueAs(JacksonMapper.LIST_TYPE_REFERENCE);
                s = MAPPER.writeValueAsString(list);
            } else if (p.isExpectedStartObjectToken()) {
                Map<String, Object> list = p.readValueAs(JacksonMapper.MAP_TYPE_REFERENCE);
                s = MAPPER.writeValueAsString(list);
            } else {
                s = p.getValueAsString();
            }
            return new Property<>(s);
        }
    }

    @SuppressWarnings("rawtypes")
    static class PropertySerializer extends StdSerializer<Property> {
        @Serial
        private static final long serialVersionUID = 1L;

        protected PropertySerializer() {
            super(Property.class);
        }

        @Override
        public void serialize(Property value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(value.getExpression());
        }
    }
}
