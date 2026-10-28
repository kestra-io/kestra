package io.kestra.jdbc;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.jooq.JSONB;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.serializers.JacksonMapper;

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

    @ParameterizedTest
    @CsvSource(
        {
            "x\\uD835 y, x\\uFFFD y",
            "\\udc59, \\uFFFD",
            "\\uD835\\u0000, \\uFFFD",
            "\\uD835\\uD835\\uDC59, \\uFFFD\\uD835\\uDC59",
            "\\uD835\\\\uDC59, \\uFFFD\\\\uDC59",
            "a\\u0000b, ab"
        }
    )
    void shouldReplaceLoneSurrogateEscapesGivenUnicodeEscapes(String value, String expected) {
        assertThat(JdbcJsonbUtils.sanitize("{\"key\":\"" + value + "\"}")).isEqualTo("{\"key\":\"" + expected + "\"}");
    }

    @Test
    void shouldKeepSurrogatePairWhenSerializedByJackson() throws JsonProcessingException {
        String json = new String(MAPPER.writeValueAsBytes(Map.of("key", "\uD835\uDC59")), StandardCharsets.UTF_8);

        assertThat(json).contains("\\uD835\\uDC59");
        assertThat(JdbcJsonbUtils.valueOf(json).data()).isEqualTo(json);
    }

    @Test
    void shouldNotFixTextWhenBackslashIsEscaped() throws JsonProcessingException {
        // The text backslash-u-0000 and backslash-u-D835 are serialized with an escaped backslash, which PostgreSQL accepts.
        String json = MAPPER.writeValueAsString(Map.of("key", "a\\u0000b \\uD835"));

        assertThat(JdbcJsonbUtils.sanitize(json)).isEqualTo(json);
    }

    @Test
    void shouldReturnTheSameInstanceWhenNothingNeedsFixing() {
        String json = "{\"key\":\"caf\u00e9 \\uD835\\uDC59 \\n\"}";

        assertThat(JdbcJsonbUtils.sanitize(json)).isSameAs(json);
    }
}
