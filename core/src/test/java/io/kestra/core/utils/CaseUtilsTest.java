package io.kestra.core.utils;

import static io.kestra.core.utils.CaseUtils.camelToSnake;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class CaseUtilsTest {

    @Test
    void shouldConvertCamelCaseToSnakeCase() {
        assertThat(camelToSnake("externalId")).isEqualTo("external_id");
        assertThat(camelToSnake("flowRevisionId")).isEqualTo("flow_revision_id");
        assertThat(camelToSnake("createdAt")).isEqualTo("created_at");
    }

    @Test
    void shouldHandleSingleWord() {
        assertThat(camelToSnake("id")).isEqualTo("id");
        assertThat(camelToSnake("name")).isEqualTo("name");
    }

    @Test
    void shouldHandleAcronyms() {
        assertThat(camelToSnake("URLValue")).isEqualTo("u_r_l_value");
    }

    @Test
    void shouldHandleAlreadySnakeCase() {
        assertThat(camelToSnake("external_id")).isEqualTo("external_id");
    }

}
