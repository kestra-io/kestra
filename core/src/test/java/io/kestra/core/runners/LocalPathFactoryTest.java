package io.kestra.core.runners;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.kestra.core.runners.configuration.LocalFilesConfiguration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalPathFactoryTest {
    @TempDir
    Path directory;

    @Test
    void shouldExplainConfigurationWhenPluginPathIsDenied() throws IOException {
        Path file = Files.writeString(directory.resolve("input.txt"), "input");
        LocalPath localPath = factory().createLocalPath(runContext());

        assertThatThrownBy(() -> localPath.get(file.toUri()))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining(file.toRealPath().toString())
            .hasMessageContaining(LocalPath.ALLOWED_PATHS_CONFIG)
            .hasMessageContaining("`allowed-paths` plugin configuration")
            .hasMessageContaining("https://go.kestra.io/adjust-configuration");
    }

    @Test
    void shouldExplainConfigurationWhenGlobalPathIsDenied() throws IOException {
        Path file = Files.writeString(directory.resolve("input.txt"), "input");

        assertThatThrownBy(() -> factory().createLocalPath().get(file.toUri()))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining(LocalPath.ALLOWED_PATHS_CONFIG)
            .hasMessageContaining("https://go.kestra.io/adjust-configuration");
    }

    private RunContext runContext() throws IOException {
        RunContext runContext = mock(RunContext.class);
        WorkingDir workingDir = mock(WorkingDir.class);
        when(runContext.workingDir()).thenReturn(workingDir);
        when(workingDir.path(true)).thenReturn(Files.createDirectory(directory.resolve("working")).toRealPath());
        when(runContext.pluginConfiguration("allowed-paths")).thenReturn(Optional.empty());
        return runContext;
    }

    private LocalPathFactory factory() {
        return new LocalPathFactory(new LocalFilesConfiguration(List.of(), true, true));
    }
}
