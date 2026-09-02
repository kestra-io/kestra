package io.kestra.core.serializers;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.DefaultTimeZone;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonMapperTest {
    Pojo pojo() {
        return new Pojo(
            "te\n\nst",
            Instant.parse("2013-09-08T16:19:12Z"),
            ZonedDateTime.parse("2013-09-08T16:19:12+03:00"),
            null
        );
    }

    @Test
    @DefaultTimeZone("Europe/Athens")
    void json() throws IOException {
        ObjectMapper mapper = JacksonMapper
            .ofJson()
            .copy()
            .setTimeZone(TimeZone.getDefault());

        Pojo original = pojo();

        String s = mapper.writeValueAsString(original);
        Pojo deserialize = mapper.readValue(s, Pojo.class);

        test(original, deserialize);
    }

    @Test
    @DefaultTimeZone("Europe/Athens")
    void ion() throws IOException {
        ObjectMapper mapper = JacksonMapper.ofIon();

        Pojo original = pojo();

        String s = mapper.writeValueAsString(original);
        assertThat(s).contains("nullable:null");
        Pojo deserialize = mapper.readValue(s, Pojo.class);
        test(original, deserialize);
    }

    @Test
    void toList() throws JsonProcessingException {
        String list = "[1, 2, 3]";

        List<Object> integerList = JacksonMapper.toList(list);

        assertThat(integerList.size()).isEqualTo(3);
        assertThat(integerList).containsExactlyInAnyOrder(1, 2, 3);
    }

    @Test
    void toMap() throws JsonProcessingException {
        assertThat(JacksonMapper.toMap("""
            {
                "some": "property",
                "another": "property"
            }""")).isEqualTo(
            Map.of(
                "some", "property",
                "another", "property"
            )
        );
    }

    void test(Pojo original, Pojo deserialize) {
        assertThat(deserialize.getString()).isEqualTo(original.getString());
        assertThat(deserialize.getInstant().toEpochMilli()).isEqualTo(original.getInstant().toEpochMilli());
        assertThat(deserialize.getInstant().toString()).isEqualTo(original.getInstant().toString());
        assertThat(deserialize.getZonedDateTime().toEpochSecond()).isEqualTo(original.getZonedDateTime().toEpochSecond());
        assertThat(deserialize.getZonedDateTime().getOffset()).isEqualTo(original.getZonedDateTime().getOffset());
    }

    @Test
    void shouldComputeDiffGivenCreatedObject() {
        Pair<JsonNode, JsonNode> value = JacksonMapper.getBiDirectionalDiffs(null, new DummyObject("value"));
        // patch
        assertThat(value.getLeft().toString()).isEqualTo("[{\"op\":\"replace\",\"path\":\"\",\"value\":{\"value\":\"value\"}}]");
        // Revert
        assertThat(value.getRight().toString()).isEqualTo("[{\"op\":\"replace\",\"path\":\"\",\"value\":null}]");
    }

    @Test
    void shouldComputeDiffGivenUpdatedObject() {
        Pair<JsonNode, JsonNode> value = JacksonMapper.getBiDirectionalDiffs(new DummyObject("before"), new DummyObject("after"));
        // patch
        assertThat(value.getLeft().toString()).isEqualTo("[{\"op\":\"replace\",\"path\":\"/value\",\"value\":\"after\"}]");
        // Revert
        assertThat(value.getRight().toString()).isEqualTo("[{\"op\":\"replace\",\"path\":\"/value\",\"value\":\"before\"}]");
    }

    @Test
    void shouldComputeDiffGivenDeletedObject() {
        Pair<JsonNode, JsonNode> value = JacksonMapper.getBiDirectionalDiffs(new DummyObject("value"), null);
        // Patch
        assertThat(value.getLeft().toString()).isEqualTo("[{\"op\":\"replace\",\"path\":\"\",\"value\":null}]");
        // Revert
        assertThat(value.getRight().toString()).isEqualTo("[{\"op\":\"replace\",\"path\":\"\",\"value\":{\"value\":\"value\"}}]");
    }

    @Test
    void toMapKeepingNullValuesShouldKeepNullMapEntriesButDropNullProperties() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("a", 1);
        nested.put("b", null);

        Map<String, Object> result = JacksonMapper.toMapKeepingNullValues(
            new NullContentPojo(List.of(nested), null)
        );

        assertThat(result).doesNotContainKey("nullable");

        @SuppressWarnings("unchecked")
        Map<String, Object> record = (Map<String, Object>) ((List<Object>) result.get("records")).getFirst();
        assertThat(record).containsEntry("a", 1);
        assertThat(record).containsKey("b");
        assertThat(record.get("b")).isNull();
    }

    @Test
    void toMapKeepingNullValuesShouldKeepNullMapEntriesInTheGivenZone() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("b", null);

        // the zoned overload copies the mapper, which must not lose the content inclusion
        Map<String, Object> result = JacksonMapper.toMapKeepingNullValues(
            new NullContentPojo(List.of(nested), null), ZoneId.of("Asia/Tokyo")
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> record = (Map<String, Object>) ((List<Object>) result.get("records")).getFirst();
        assertThat(record).containsKey("b");
        assertThat(record.get("b")).isNull();
    }

    @Test
    void toMapShouldDropNullMapEntries() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("a", 1);
        nested.put("b", null);

        Map<String, Object> result = JacksonMapper.toMap(new NullContentPojo(List.of(nested), null));

        @SuppressWarnings("unchecked")
        Map<String, Object> record = (Map<String, Object>) ((List<Object>) result.get("records")).getFirst();
        assertThat(record).doesNotContainKey("b");
    }

    @Getter
    @AllArgsConstructor
    public static class NullContentPojo {
        private List<Object> records;
        private String nullable;
    }

    private record DummyObject(String value) {
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pojo {
        private String string;
        private Instant instant;
        private ZonedDateTime zonedDateTime;
        private String nullable;
    }
}
