package io.kestra.plugin.core.dashboard.chart.mardown.sources;

import org.junit.jupiter.api.Test;

import io.kestra.core.serializers.JacksonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownSourceTest {
    @Test
    void shouldSerializeTypeOnlyOnce() throws Exception {
        String jsonInput = "{\"type\":\"Text\",\"content\":\"hello\"}";
        MarkdownSource source = JacksonMapper.ofJson().readValue(jsonInput, MarkdownSource.class);
        String jsonOutput = JacksonMapper.ofJson().writeValueAsString(source);

        // the word "type" should appear exactly once in the JSON as the key "type"
        int typeCount = jsonOutput.split("\"type\"").length - 1;
        assertThat(typeCount).isEqualTo(1);
        assertThat(jsonOutput).contains("\"type\":\"Text\"");
    }
}
