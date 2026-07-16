
package io.kestra.core.serializers;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.lang3.tuple.Pair;

import com.amazon.ion.IonSystem;
import com.amazon.ion.system.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import com.github.fge.jsonpatch.diff.JsonDiff;

import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.plugins.PluginModule;
import io.kestra.core.serializers.ion.IonFactory;
import io.kestra.core.serializers.ion.IonModule;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.dataformat.ion.IonObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.datatype.guava.GuavaModule;

import static tools.jackson.core.StreamReadConstraints.DEFAULT_MAX_STRING_LEN;

public final class JacksonMapper {
    public static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() {
    };
    public static final TypeReference<List<Object>> LIST_TYPE_REFERENCE = new TypeReference<>() {
    };
    public static final TypeReference<Object> OBJECT_TYPE_REFERENCE = new TypeReference<>() {
    };

    private JacksonMapper() {
    }

    static {
        StreamReadConstraints.overrideDefaultStreamReadConstraints(
            StreamReadConstraints.builder().maxNameLength(DEFAULT_MAX_STRING_LEN).build()
        );
    }

    // Kept on Jackson 2: com.github.java-json-tools:json-patch has no Jackson 3 release.
    // Used only by getBiDirectionalDiffs/applyPatchesOnJsonNode below.
    private static final com.fasterxml.jackson.databind.ObjectMapper DIFF_MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    private static final ObjectMapper MAPPER = JacksonMapper.configure(JsonMapper.builder()).build();

    private static final ObjectMapper NON_STRICT_MAPPER = MAPPER.rebuild()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .build();

    public static ObjectMapper ofJson() {
        return JacksonMapper.ofJson(false);
    }

    public static ObjectMapper ofJson(boolean strict) {
        return strict ? MAPPER : NON_STRICT_MAPPER;
    }

    private static final ObjectMapper YAML_MAPPER = JacksonMapper.configure(
        YAMLMapper.builder()
            .configure(tools.jackson.dataformat.yaml.YAMLWriteFeature.MINIMIZE_QUOTES, true)
            .configure(tools.jackson.dataformat.yaml.YAMLWriteFeature.WRITE_DOC_START_MARKER, false)
            .configure(tools.jackson.dataformat.yaml.YAMLWriteFeature.USE_NATIVE_TYPE_ID, false)
            .configure(tools.jackson.dataformat.yaml.YAMLWriteFeature.SPLIT_LINES, false)
    )
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .build();

    public static ObjectMapper ofYaml() {
        return YAML_MAPPER;
    }

    public static Map<String, Object> toMap(Object object, ZoneId zoneId) {
        return MAPPER.rebuild()
            .defaultTimeZone(TimeZone.getTimeZone(zoneId.getId()))
            .build()
            .convertValue(object, MAP_TYPE_REFERENCE);
    }

    public static Map<String, Object> toMap(Object object) {
        return MAPPER.convertValue(object, MAP_TYPE_REFERENCE);
    }

    public static <T> T toMap(Object map, Class<T> cls) {
        return MAPPER.convertValue(map, cls);
    }

    public static Map<String, Object> toMap(String json) throws JacksonException {
        return MAPPER.readValue(json, MAP_TYPE_REFERENCE);
    }

    public static List<Object> toList(String json) throws JacksonException {
        return MAPPER.readValue(json, LIST_TYPE_REFERENCE);
    }

    public static List<String> toList(Object object) {
        return MAPPER.convertValue(object, new TypeReference<>() {
        });
    }

    public static Object toObject(String json) throws JacksonException {
        return MAPPER.readValue(json, OBJECT_TYPE_REFERENCE);
    }

    public static <T> T cast(Object object, Class<T> cls) throws JacksonException {
        return MAPPER.readValue(MAPPER.writeValueAsString(object), cls);
    }

    public static <T> String log(T Object) {
        try {
            return YAML_MAPPER.writeValueAsString(Object);
        } catch (JacksonException ignored) {
            return "Failed to log " + Object.getClass();
        }
    }

    private static final ObjectMapper ION_MAPPER = createIonObjectMapper(false);
    private static final ObjectMapper ION_BINARY_MAPPER = createIonObjectMapper(true);

    public static ObjectMapper ofIon() {
        return ION_MAPPER;
    }

    public static ObjectMapper ofIonBinary() {
        return ION_BINARY_MAPPER;
    }

    private static <B extends tools.jackson.databind.cfg.MapperBuilder<?, B>> B configure(B builder) {
        SimpleModule customModule = new SimpleModule();
        customModule.addDeserializer(Duration.class, new DurationDeserializer());
        // Jackson 3 no longer inherits a getter's @JsonDeserialize(as=...) from an implemented
        // interface onto a record's canonical-constructor parameter of the same name (used e.g. by
        // io.kestra.core.scheduler.events.TriggerEvent#id()), so records deserializing a bare
        // TriggerId field fail with "no Creators exist" for the interface. Map the abstract type
        // explicitly so all TriggerEvent subtypes (and any other TriggerId-typed field) still resolve.
        customModule.addAbstractTypeMapping(TriggerId.class, TriggerId.Default.class);

        return builder
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            // Jackson 3 flips several defaults relative to Jackson 2; re-assert the previous behavior.
            .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            // Without this, Jackson 3 silently ignores final fields with a @Builder.Default initializer
            // (e.g. Property<T> fields on plugin tasks), leaving them at their default instead of the parsed value.
            .enable(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS)
            // A null value for a primitive (e.g. a final boolean field) should fall back to its default, not throw.
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
            .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
            // java.time, java.util.Optional and constructor-parameter-name support are embedded in jackson-databind 3.x
            .addModule(new GuavaModule())
            .addModule(new PluginModule())
            .addModule(customModule)
            .defaultTimeZone(TimeZone.getDefault());
    }

    private static ObjectMapper createIonObjectMapper(boolean binary) {
        IonFactory ionFactory = new IonFactory(createIonSystem(), binary);
        return configure(IonObjectMapper.builder(ionFactory))
            .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.ALWAYS))
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .addModule(new IonModule())
            .build();
    }

    private static IonSystem createIonSystem() {
        return IonSystemBuilder.standard()
            .withIonTextWriterBuilder(IonTextWriterBuilder.standard().withWriteTopLevelValuesOnNewLines(true))
            .build();
    }

    public static Pair<JsonNode, JsonNode> getBiDirectionalDiffs(Object before, Object after) {
        JsonNode beforeNode = DIFF_MAPPER.valueToTree(before);
        JsonNode afterNode = DIFF_MAPPER.valueToTree(after);

        JsonNode patch = JsonDiff.asJson(beforeNode, afterNode);
        JsonNode revert = JsonDiff.asJson(afterNode, beforeNode);

        return Pair.of(patch, revert);
    }

    public static JsonNode applyPatchesOnJsonNode(JsonNode jsonObject, List<JsonNode> patches) {
        for (JsonNode patch : patches) {
            try {
                // Required for ES
                if (patch.findValue("value") == null && !patch.isEmpty()) {
                    ((ObjectNode) patch.get(0)).set("value", null);
                }
                jsonObject = JsonPatch.fromJson(patch).apply(jsonObject);
            } catch (java.io.IOException | JsonPatchException e) {
                throw new RuntimeException(e);
            }
        }
        return jsonObject;
    }
}
