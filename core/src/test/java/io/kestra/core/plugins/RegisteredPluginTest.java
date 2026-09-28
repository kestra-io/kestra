package io.kestra.core.plugins;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.plugin.core.log.Log;

import static org.assertj.core.api.Assertions.assertThat;

class RegisteredPluginTest {
    private static RegisteredPlugin core() {
        return new PluginScanner(RegisteredPluginTest.class.getClassLoader()).scan();
    }

    @Test
    void shouldDetectMonochromeIconFromCurrentColor() {
        Optional<RegisteredPlugin.IconAndMonochrome> icon = core().iconAndMonochrome("io.kestra.plugin.core.debug.Echo");

        assertThat(icon).isPresent();
        assertThat(icon.get().monochrome()).isTrue();
        assertThat(icon.get().icon()).isNotNull();
    }

    @Test
    void shouldNotFlagFixedColorIconAsMonochrome() {
        Optional<RegisteredPlugin.IconAndMonochrome> icon = core().iconAndMonochrome(Log.class);

        assertThat(icon).isPresent();
        assertThat(icon.get().monochrome()).isFalse();
    }

    @Test
    void shouldReturnEmptyWhenIconDoesNotExist() {
        Optional<RegisteredPlugin.IconAndMonochrome> icon = core().iconAndMonochrome("io.kestra.plugin.unknown.Task");

        assertThat(icon).isEmpty();
    }

    @Test
    void shouldResolveCoreIconForIsolatedPluginClassLoader() throws Exception {
        RegisteredPlugin plugin = RegisteredPlugin.builder()
            .classLoader(PluginClassLoader.of(new URL("file:/missing-plugin.jar"), new URL[0], RegisteredPluginTest.class.getClassLoader()))
            .build();

        Optional<RegisteredPlugin.IconAndMonochrome> icon = plugin.iconAndMonochrome("io.kestra.plugin.kestra.ee.assets");

        assertThat(icon).isPresent();
        assertThat(new String(Base64.getDecoder().decode(icon.get().icon()), StandardCharsets.UTF_8))
            .contains("viewBox=\"0 0 80 80\"");
    }
}
