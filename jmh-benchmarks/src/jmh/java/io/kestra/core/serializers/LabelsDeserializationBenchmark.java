package io.kestra.core.serializers;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.kestra.core.models.Label;

/**
 * Compares the label deserializer that went through an intermediate {@code List<Map>} or {@code Map} plus streams
 * with the token-based one, on the five labels of a typical execution, in both the list form and the legacy map form.
 *
 * <p>
 * Result (JDK 25, 1 fork, 3 warmup iterations of 2 s and 5 measured iterations of 3 s, {@code -prof gc}):
 * <pre>
 *                  map-based             token-based
 * list form        1005 ns, 3520 B/op    478 ns, 1416 B/op
 * map form         686 ns, 2064 B/op     282 ns, 1120 B/op
 * </pre>
 *
 * @see <a href="https://github.com/kestra-io/kestra/pull/22484">KESTRA#22484</a>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class LabelsDeserializationBenchmark {
    private static final String LIST = """
        {"labels": [{"key": "env", "value": "prod"}, {"key": "team", "value": "data"}, {"key": "region", "value": "eu-west-1"}, {"key": "priority", "value": 1}, {"key": "critical", "value": true}]}""";
    private static final String MAP = """
        {"labels": {"env": "prod", "team": "data", "region": "eu-west-1", "priority": 1, "critical": true}}""";

    public record OldHolder(@JsonDeserialize(using = MapBasedListOrMapOfLabelDeserializer.class) List<Label> labels) {
    }

    public record NewHolder(@JsonDeserialize(using = ListOrMapOfLabelDeserializer.class) List<Label> labels) {
    }

    private ObjectMapper mapper;

    @Setup
    public void setup() {
        mapper = JacksonMapper.ofJson();
    }

    @Benchmark
    public List<Label> oldListForm() throws IOException {
        return mapper.readValue(LIST, OldHolder.class).labels();
    }

    @Benchmark
    public List<Label> newListForm() throws IOException {
        return mapper.readValue(LIST, NewHolder.class).labels();
    }

    @Benchmark
    public List<Label> oldMapForm() throws IOException {
        return mapper.readValue(MAP, OldHolder.class).labels();
    }

    @Benchmark
    public List<Label> newMapForm() throws IOException {
        return mapper.readValue(MAP, NewHolder.class).labels();
    }

    /** The deserializer as it was before KESTRA#22484. */
    public static class MapBasedListOrMapOfLabelDeserializer extends JsonDeserializer<List<Label>> {
        @SuppressWarnings("unchecked")
        @Override
        public List<Label> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (p.hasToken(JsonToken.VALUE_NULL)) {
                return null;
            } else if (p.hasToken(JsonToken.START_ARRAY)) {
                List<Map<String, String>> ret = ctxt.readValue(p, List.class);
                return ret.stream().map(map ->
                {
                    Object value = map.get("value");
                    if (isAllowedType(value)) {
                        return new Label(map.get("key"), String.valueOf(value));
                    } else {
                        throw new IllegalArgumentException("Unsupported type for key: " + map.get("key") + ", value: " + value);
                    }
                }).toList();
            } else if (p.hasToken(JsonToken.START_OBJECT)) {
                Map<String, Object> ret = ctxt.readValue(p, Map.class);
                return ret == null ? null
                    : ret.entrySet().stream()
                        .map(entry ->
                        {
                            Object value = entry.getValue();
                            if (isAllowedType(value)) {
                                return new Label(entry.getKey(), String.valueOf(value));
                            }
                            throw new IllegalArgumentException("Unsupported type for key: " + entry.getKey() + ", value: " + value);
                        })
                        .toList();
            }
            throw new IllegalArgumentException("Unable to deserialize value as it's neither an object neither an array");
        }

        private static boolean isAllowedType(Object value) {
            return value instanceof String ||
                value instanceof Integer ||
                value instanceof Long ||
                value instanceof Float ||
                value instanceof Double ||
                value instanceof Boolean;
        }
    }
}
