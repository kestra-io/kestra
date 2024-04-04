
package io.kestra.core.serializers;

import static com.amazon.ion.impl.lite._Private_LiteDomTrampoline.newLiteSystem;

import com.amazon.ion.IonSystem;
import com.amazon.ion.impl._Private_IonBinaryWriterBuilder;
import com.amazon.ion.impl._Private_Utils;
import com.amazon.ion.system.IonBinaryWriterBuilder;
import com.amazon.ion.system.IonReaderBuilder;
import com.amazon.ion.system.IonSystemBuilder;
import com.amazon.ion.system.IonTextWriterBuilder;
import com.amazon.ion.system.SimpleCatalog;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.dataformat.ion.IonObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import io.kestra.core.contexts.KestraClassLoader;
import io.kestra.core.serializers.ion.IonFactory;
import io.kestra.core.serializers.ion.IonModule;
import org.yaml.snakeyaml.LoaderOptions;

import java.time.ZoneId;
import java.util.Map;
import java.util.TimeZone;

public final class JacksonMapper {

    private JacksonMapper() {}

    private static final ObjectMapper MAPPER = JacksonMapper.configure(
        new ObjectMapper()
    );

    private static final ObjectMapper NON_STRICT_MAPPER = MAPPER
        .copy()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static ObjectMapper ofJson() {
        return MAPPER;
    }

    public static ObjectMapper ofJson(boolean strict) {
        return strict ? MAPPER : NON_STRICT_MAPPER;
    }

    private static final ObjectMapper YAML_MAPPER = JacksonMapper.configure(
        new ObjectMapper(
            YAMLFactory
                .builder()
                .loaderOptions(new LoaderOptions())
                .configure(YAMLGenerator.Feature.MINIMIZE_QUOTES, true)
                .configure(YAMLGenerator.Feature.WRITE_DOC_START_MARKER, false)
                .configure(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID, false)
                .configure(YAMLGenerator.Feature.SPLIT_LINES, false)
                .build()
        )
    );

    public static ObjectMapper ofYaml() {
        return YAML_MAPPER;
    }

    private static final TypeReference<Map<String, Object>> TYPE_REFERENCE = new TypeReference<>() {};

    public static Map<String, Object> toMap(Object object, ZoneId zoneId) {
        return MAPPER
            .copy()
            .setTimeZone(TimeZone.getTimeZone(zoneId.getId()))
            .convertValue(object, TYPE_REFERENCE);
    }

    public static Map<String, Object> toMap(Object object) {
        return MAPPER.convertValue(object, TYPE_REFERENCE);
    }

    public static <T> T toMap(Object map, Class<T> cls) {
        return MAPPER.convertValue(map, cls);
    }

    public static Map<String, Object> toMap(String json) throws JsonProcessingException {
        return MAPPER.readValue(json, TYPE_REFERENCE);
    }

    private static final TypeReference<Object> TYPE_REFERENCE_OBJECT = new TypeReference<>() {};

    public static Object toObject(String json) throws JsonProcessingException {
        return MAPPER.readValue(json, TYPE_REFERENCE_OBJECT);
    }

    public static <T> T cast(Object object, Class<T> cls) throws JsonProcessingException {
        return MAPPER.readValue(MAPPER.writeValueAsString(object), cls);
    }

    public static <T> String log(T Object) {
        try {
            return YAML_MAPPER.writeValueAsString(Object);
        } catch (JsonProcessingException ignored) {
            return "Failed to log " + Object.getClass();
        }
    }

    private static final ObjectMapper ION_MAPPER = createIonObjectMapper();

    public static ObjectMapper ofIon() {
        return ION_MAPPER;
    }

    private static ObjectMapper configure(ObjectMapper mapper) {
        // unit test can be not init
        if (KestraClassLoader.isInit()) {
            TypeFactory tf = TypeFactory.defaultInstance().withClassLoader(KestraClassLoader.instance());
            mapper.setTypeFactory(tf);
        }

        return mapper
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
            .registerModule(new JavaTimeModule())
            .registerModule(new Jdk8Module())
            .registerModule(new ParameterNamesModule())
            .registerModules(new GuavaModule())
            .setTimeZone(TimeZone.getDefault());
    }

    private static ObjectMapper createIonObjectMapper() {
        return configure(new IonObjectMapper(new IonFactory(createIonSystem())))
            .setSerializationInclusion(JsonInclude.Include.ALWAYS)
            .registerModule(new IonModule());
    }

    /**
     * @see IonSystemBuilder#build()
     */
    // TODO simplify after https://github.com/amazon-ion/ion-java/pull/781
    private static IonSystem createIonSystem() {
        final var catalog = new SimpleCatalog();

        final var textWriterBuilder = IonTextWriterBuilder.standard()
            .withCatalog(catalog)
            .withCharsetAscii()
            .withWriteTopLevelValuesOnNewLines(true); // write line separators on new lines instead of spaces

        final var binaryWriterBuilder = IonBinaryWriterBuilder.standard()
            .withCatalog(catalog)
            .withInitialSymbolTable(_Private_Utils.systemSymtab(1));

        final var readerBuilder = IonReaderBuilder.standard()
            .withCatalog(catalog);

        return newLiteSystem(textWriterBuilder, (_Private_IonBinaryWriterBuilder) binaryWriterBuilder, readerBuilder);
    }
}
