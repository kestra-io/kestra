package io.kestra.jdbc;

import org.jooq.JSONB;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcJsonbUtilsTest {
    @Test
    void shouldReturnNullForNullInput() {
        assertThat(JdbcJsonbUtils.valueOf(null)).isNull();
    }

    @Test
    void shouldStripNullBytes() {
        // Given
        String jsonWithNullBytes = "{\"key\":\"value\u0000with\u0000nulls\"}";

        // When
        JSONB result = JdbcJsonbUtils.valueOf(jsonWithNullBytes);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.data()).isEqualTo("{\"key\":\"valuewithnulls\"}");
        assertThat(result.data()).doesNotContain("\u0000");
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
    void shouldHandleJsonWithOnlyNullByte() {
        // Given
        String onlyNullByte = "\u0000";

        // When
        JSONB result = JdbcJsonbUtils.valueOf(onlyNullByte);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.data()).isEmpty();
    }
}
