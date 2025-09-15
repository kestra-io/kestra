package io.kestra.core.serializers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.DefaultTimeZone;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

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
            }""")).isEqualTo(Map.of(
                "some", "property",
            "another", "property"
        ));
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
    
    private record DummyObject(String value){}
    

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
