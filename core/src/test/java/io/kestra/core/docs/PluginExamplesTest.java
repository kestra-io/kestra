package io.kestra.core.docs;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.plugins.PluginScanner;
import io.kestra.core.serializers.YamlParser;

import static org.assertj.core.api.Assertions.assertThat;

class PluginExamplesTest {

    @Test
    void shouldParseEveryFullYamlExampleOfCorePlugins() {
        URL testClasses = codeLocation(PluginExamplesTest.class);
        // Test fixtures such as BadExecutable carry deliberately invalid examples.
        List<Class<?>> pluginClasses = new PluginScanner(PluginExamplesTest.class.getClassLoader()).scan()
            .allClass()
            .stream()
            .<Class<?>>map(cls -> cls)
            .filter(cls -> !testClasses.equals(codeLocation(cls)))
            .toList();

        List<String> failures = pluginClasses.stream()
            .filter(cls -> cls.isAnnotationPresent(Plugin.class))
            .flatMap(cls -> Arrays.stream(cls.getAnnotation(Plugin.class).examples())
                .filter(example -> example.full() && "yaml".equals(example.lang()))
                .map(example -> parseFailure(cls, example)))
            .filter(failure -> failure != null)
            .toList();

        assertThat(pluginClasses).isNotEmpty();
        assertThat(failures).isEmpty();
    }

    private static URL codeLocation(Class<?> cls) {
        return cls.getProtectionDomain().getCodeSource().getLocation();
    }

    private static String parseFailure(Class<?> cls, Example example) {
        try {
            YamlParser.parse(String.join("\n", example.code()), Map.class);
            return null;
        } catch (RuntimeException e) {
            return "%s example '%s': %s".formatted(cls.getName(), example.title(), e.getMessage());
        }
    }
}
