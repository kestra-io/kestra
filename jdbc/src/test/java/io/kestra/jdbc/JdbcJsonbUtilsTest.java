package io.kestra.jdbc;

import java.util.Map;

import org.jooq.JSONB;
import org.junit.jupiter.api.Test;

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

    @Test
    void shouldReplaceLoneHighSurrogateWrittenByJackson() throws JsonProcessingException {
        // Given - the formula from kestra#14806, cut in the middle of a surrogate pair. Jackson writes the lone
        // surrogate as a raw character, which PostgreSQL rejects in a JSONB value.
        String json = MAPPER.writeValueAsString(Map.of("key", "x\uD835 = lower limit"));

        // When
        JSONB result = JdbcJsonbUtils.valueOf(json);

        // Then
        assertThat(result.data()).isEqualTo("{\"key\":\"x\uFFFD = lower limit\"}");
    }

    @Test
    void shouldReplaceLoneLowSurrogateWrittenByJackson() throws JsonProcessingException {
        // Given
        String json = MAPPER.writeValueAsString(Map.of("key", "\uDC59 test"));

        // When
        JSONB result = JdbcJsonbUtils.valueOf(json);

        // Then
        assertThat(result.data()).isEqualTo("{\"key\":\"\uFFFD test\"}");
    }

    @Test
    void shouldReplaceJsonEscapedLoneSurrogates() {
        // Given - lone surrogates written as JSON escapes, as an escaping serializer or a client would send them
        String json = "{\"high\":\"x\\uD835 y\",\"low\":\"\\udc59\"}";

        // When
        JSONB result = JdbcJsonbUtils.valueOf(json);

        // Then
        assertThat(result.data()).isEqualTo("{\"high\":\"x\\uFFFD y\",\"low\":\"\\uFFFD\"}");
    }

    @Test
    void shouldKeepValidSurrogatePairs() throws JsonProcessingException {
        // Given - U+1D459 MATHEMATICAL ITALIC SMALL L, both as JSON escapes and as raw characters
        String escaped = "{\"key\":\"\\uD835\\uDC59\"}";
        String raw = MAPPER.writeValueAsString(Map.of("key", "\uD835\uDC59"));

        // When / Then
        assertThat(JdbcJsonbUtils.valueOf(escaped).data()).isEqualTo(escaped);
        assertThat(JdbcJsonbUtils.valueOf(raw).data()).isEqualTo(raw);
    }

    @Test
    void shouldReplaceRawLoneSurrogates() {
        // Given - raw lone surrogates that did not go through Jackson escaping (defensive)
        String json = "{\"key\":\"a\uD800b\uDC00c\"}";

        // When
        JSONB result = JdbcJsonbUtils.valueOf(json);

        // Then
        assertThat(result.data()).isEqualTo("{\"key\":\"a\uFFFDb\uFFFDc\"}");
    }

    @Test
    void shouldNotTreatAnEscapedBackslashFollowedByUAsAnEscape() {
        // Given - a literal backslash followed by the text uD835, which is not an escape
        String json = "{\"key\":\"\\\\uD835\"}";

        // When
        JSONB result = JdbcJsonbUtils.valueOf(json);

        // Then
        assertThat(result.data()).isEqualTo(json);
    }
}
