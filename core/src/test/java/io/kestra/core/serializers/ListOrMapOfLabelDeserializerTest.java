package io.kestra.core.serializers;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.kestra.core.models.Label;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListOrMapOfLabelDeserializerTest {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    record Holder(@JsonDeserialize(using = ListOrMapOfLabelDeserializer.class) List<Label> labels) {
    }

    @Test
    void shouldDeserializeAListOfLabelsWithScalarValues() throws Exception {
        Holder holder = MAPPER.readValue(
            """
                {"labels": [{"key": "env", "value": "prod"}, {"key": "count", "value": 3}, {"key": "ratio", "value": 1.5}, {"key": "flag", "value": true}]}""",
            Holder.class
        );

        assertThat(holder.labels()).containsExactly(
            new Label("env", "prod"),
            new Label("count", "3"),
            new Label("ratio", "1.5"),
            new Label("flag", "true")
        );
    }

    @Test
    void shouldDeserializeAMapOfLabels() throws Exception {
        Holder holder = MAPPER.readValue("""
            {"labels": {"env": "prod", "count": 3}}""", Holder.class);

        assertThat(holder.labels()).containsExactly(new Label("env", "prod"), new Label("count", "3"));
    }

    @Test
    void shouldReturnNullWhenLabelsAreNull() throws Exception {
        assertThat(MAPPER.readValue("""
            {"labels": null}""", Holder.class).labels()).isNull();
    }

    @Test
    void shouldRejectALabelWhoseValueIsNotAScalar() {
        assertThatThrownBy(() -> MAPPER.readValue("""
            {"labels": [{"key": "env", "value": {"nested": true}}]}""", Holder.class))
            .hasMessageContaining("Unsupported type for key: env");
    }
}
