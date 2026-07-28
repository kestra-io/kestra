package io.kestra.core.models.flows.input;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.serializers.JacksonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ValueOptionTest {

    private static final ObjectMapper YAML = JacksonMapper.ofYaml();
    private static final ObjectMapper JSON = JacksonMapper.ofJson();

    @Test
    void shouldDeserializeYamlFromStringArray() throws Exception {
        String yaml = """
            id: aws
            type: SELECT
            values:
              - V1
              - V2
            """;

        SelectInput input = YAML.readValue(yaml, SelectInput.class);

        assertThat(input.getValues())
            .containsExactly(new ValueOption("V1", "V1"), new ValueOption("V2", "V2"));
    }

    @Test
    void shouldDeserializeYamlFromObjectArray() throws Exception {
        String yaml = """
            id: aws
            type: SELECT
            values:
              - label: Production (Main)
                value: "123456789"
              - label: Staging (Sandbox)
                value: "987654321"
            """;

        SelectInput input = YAML.readValue(yaml, SelectInput.class);

        assertThat(input.getValues())
            .containsExactly(
                new ValueOption("Production (Main)", "123456789"),
                new ValueOption("Staging (Sandbox)", "987654321")
            );
    }

    @Test
    void shouldDeserializeMixedScalarsAndObjects() throws Exception {
        String yaml = """
            id: aws
            type: MULTISELECT
            values:
              - V1
              - label: Prod
                value: "123"
            """;

        MultiselectInput input = YAML.readValue(yaml, MultiselectInput.class);

        assertThat(input.getValues())
            .containsExactly(new ValueOption("V1", "V1"), new ValueOption("Prod", "123"));
    }

    @Test
    void shouldSerializeAsStringWhenLabelEqualsValue() throws Exception {
        SelectInput input = SelectInput.builder()
            .id("id")
            .values(List.of(new ValueOption("V1", "V1"), new ValueOption("V2", "V2")))
            .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> json = JSON.convertValue(input, Map.class);
        assertThat((List<Object>) json.get("values")).containsExactly("V1", "V2");
    }

    @Test
    void shouldSerializeAsObjectWhenLabelDiffersFromValue() throws Exception {
        SelectInput input = SelectInput.builder()
            .id("id")
            .values(List.of(new ValueOption("Prod", "123"), new ValueOption("V2", "V2")))
            .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> json = JSON.convertValue(input, Map.class);
        List<?> values = (List<?>) json.get("values");
        assertThat(values).hasSize(2);
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) values.get(0);
        assertThat(first).containsEntry("label", "Prod").containsEntry("value", "123");
        assertThat(values.get(1)).isEqualTo("V2");
    }
}
