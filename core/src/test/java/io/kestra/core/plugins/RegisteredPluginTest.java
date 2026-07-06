package io.kestra.core.plugins;

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
        // "io.kestra.plugin.core.debug.Echo" ships a `fill="currentColor"` SVG resource under
        // core/src/main/resources/icons/ — resolved here by resource name, not by class, since
        // icon lookup by name doesn't require a matching registered task/trigger class.
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
}
