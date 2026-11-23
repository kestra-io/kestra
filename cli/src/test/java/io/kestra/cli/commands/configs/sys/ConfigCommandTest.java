package io.kestra.cli.commands.configs.sys;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigCommandTest {
    @Test
    void shouldNotLoadExternalPluginsForAnyConfigsCommand() {
        assertThat(new ConfigCommand().loadExternalPlugins()).isFalse();
        assertThat(new ConfigPropertiesCommand().loadExternalPlugins()).isFalse();
        assertThat(new ConfigValidateCommand().loadExternalPlugins()).isFalse();
    }
}
