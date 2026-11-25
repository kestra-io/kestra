package io.kestra.plugin.scripts.exec;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.property.Property;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractExecScriptTest {
    @Test
    void shouldKeepEnvOutOfToString() {
        // Given
        var task = Script.builder().id("script").type("script").env(Property.ofValue(Map.of("API_TOKEN", "super-secret-value"))).build();

        // Then
        assertThat(task.toString()).doesNotContain("super-secret-value").doesNotContain("API_TOKEN");
    }

    @SuperBuilder
    @NoArgsConstructor
    @Getter
    private static class Script extends AbstractExecScript {
        private Property<String> containerImage;
    }
}
