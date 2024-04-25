package io.kestra.core.models;

import io.kestra.core.models.annotations.Plugin;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class PluginTest {

    @Test
    void shouldReturnTrueForInternal() {
        Assertions.assertTrue( io.kestra.core.models.Plugin.isInternal(TestPlugin.class));
    }

    @Test
    void shouldReturnPluginId() {
        Assertions.assertEquals(Optional.of("test"), io.kestra.core.models.Plugin.getId(TestPlugin.class));
    }

    @Plugin(internal = true)
    @Plugin.Id("test")
    public static class TestPlugin implements io.kestra.core.models.Plugin {

    }
}