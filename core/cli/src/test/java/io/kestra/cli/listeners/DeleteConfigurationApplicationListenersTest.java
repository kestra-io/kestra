package io.kestra.cli.listeners;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.MapPropertySource;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeleteConfigurationApplicationListenersTest {

    @Test
    @SuppressWarnings("try")
    void run() throws IOException {
        File tempFile = File.createTempFile("test", ".yml");

        Files.write(tempFile.toPath(), "kestra.configurations.delete-files-on-start: true".getBytes());

        MapPropertySource mapPropertySource = new MapPropertySource(
            tempFile.getAbsolutePath(),
            Map.of("kestra.configurations.delete-files-on-start", true)
        );

        try (ApplicationContext ctx = ApplicationContext.run(mapPropertySource, Environment.CLI, Environment.TEST)) {
            assertThat(tempFile.exists()).isFalse();
        }
    }
}