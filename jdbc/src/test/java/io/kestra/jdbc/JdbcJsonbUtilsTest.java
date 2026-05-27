package io.kestra.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.serializers.JacksonMapper;
import org.jooq.JSONB;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcJsonbUtilsTest {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson(false);
    private static final String NULL_CHAR = String.valueOf((char) 0);

    @Test
    void shouldReturnNullForNullInput() {
        assertThat(JdbcJsonbUtils.valueOf(null)).isNull();
    }

    @Test
    void shouldStripJsonEscapedNullBytes() throws JsonProcessingException {
        String javaString = "value" + NULL_CHAR + "with" + NULL_CHAR + "nulls";
        String json = MAPPER.writeValueAsString(Map.of("key", javaString));

        // Verify Jackson produced the JSON escape (not a raw null byte)
        assertThat(json).contains("\\u0000");

        // When
        JSONB result = JdbcJsonbUtils.valueOf(json);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.data()).doesNotContain("\\u0000");
        assertThat(result.data()).isEqualTo("{\"key\":\"valuewithnulls\"}");
    }

    @Test
    void shouldStripRawNullBytes() {
        // Given - raw null bytes that didn't go through Jackson (defensive)
        String jsonWithNullBytes = "{\"key\":\"value" + NULL_CHAR + "raw\"}";

        // When
        JSONB result = JdbcJsonbUtils.valueOf(jsonWithNullBytes);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.data()).isEqualTo("{\"key\":\"valueraw\"}");
    }

    @Test
    void shouldLeaveCleanJsonUnchanged() {
        // Given
        String cleanJson = "{\"key\":\"value\",\"number\":42}";

        // When
        JSONB result = JdbcJsonbUtils.valueOf(cleanJson);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.data()).isEqualTo(cleanJson);
    }

    @Test
    void shouldHandleKafkaStylePayload() throws JsonProcessingException {
        // Given - mimics the customer scenario: Kafka header with a null byte value
        String json = MAPPER.writeValueAsString(
            Map.of("headers", Map.of("apicurio.value.globalId", NULL_CHAR))
        );

        // When
        JSONB result = JdbcJsonbUtils.valueOf(json);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.data()).doesNotContain("\\u0000");
    }
}
