package io.kestra.cli.commands.servers;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalCommandTest {

    @Test
    void shouldSetTheLocalPersonaProperties() {
        // When
        Map<String, Object> overrides = LocalCommand.propertiesOverrides();

        // Then — these are what make the computed plugin auto-install default resolve to on
        assertThat(overrides)
            .containsEntry("kestra.repository.type", "h2")
            .containsEntry("kestra.queue.type", "h2")
            .containsEntry("kestra.storage.type", "local");
    }

    @Test
    void shouldNotForcePluginAutoInstallSoItStaysDisableable() {
        // Given / When — command overrides outrank the config file and system properties, so
        // forcing the flag here would make the feature impossible to turn off on this persona
        Map<String, Object> overrides = LocalCommand.propertiesOverrides();

        // Then
        assertThat(overrides).doesNotContainKey("kestra.plugins.auto-install.enabled");
    }
}
