package io.kestra.webserver.services.ai.spi;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;

import dev.langchain4j.spi.prompt.PromptTemplateFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PebbleSafeTemplateFactoryTest {

    private final PebbleSafeTemplateFactory factory = new PebbleSafeTemplateFactory();

    @Test
    void shouldReplaceEveryOccurrenceOfEachVariableWhenRendering() {
        PromptTemplateFactory.Template template = create("{_{a}_} and {_{b}_}, then {_{a}_} again");

        String result = template.render(Map.of("a", "first", "b", "second"));

        assertThat(result).isEqualTo("first and second, then first again");
    }

    @Test
    void shouldLeavePebbleExpressionsUntouchedWhenTheyShareAVariableName() {
        // Given - Pebble syntax coming from the docs, blueprints or RAG content
        PromptTemplateFactory.Template template = create("{{name}}, {{ name }} and {{ outputs.task.value }} are Pebble, {_{name}_} is a prompt variable");

        // When
        String result = template.render(Map.of("name", "Kestra"));

        // Then
        assertThat(result).isEqualTo("{{name}}, {{ name }} and {{ outputs.task.value }} are Pebble, Kestra is a prompt variable");
    }

    @Test
    void shouldLeavePlaceholderUntouchedWhenNoValueIsGiven() {
        PromptTemplateFactory.Template template = create("Hello {_{name}_}, today is {_{day}_}");

        String result = template.render(Map.of("name", "Kestra"));

        assertThat(result).isEqualTo("Hello Kestra, today is {_{day}_}");
    }

    @Test
    void shouldInsertValueLiterallyWhenItContainsRegexReplacementCharacters() {
        PromptTemplateFactory.Template template = create("Price: {_{price}_}");

        String result = template.render(Map.of("price", "$1 \\ (.*)"));

        assertThat(result).isEqualTo("Price: $1 \\ (.*)");
    }

    @Test
    void shouldThrowWhenValueIsNull() {
        PromptTemplateFactory.Template template = create("Hello {_{name}_}");
        Map<String, Object> variables = new HashMap<>();
        variables.put("name", null);

        assertThatThrownBy(() -> template.render(variables))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("'name'");
    }

    @Test
    void shouldThrowWhenTemplateIsBlankOrNull() {
        assertThatThrownBy(() -> create("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("template");
        assertThatThrownBy(() -> create(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("template");
    }

    @Test
    void shouldBeRegisteredAsPromptTemplateFactoryService() {
        ServiceLoader<PromptTemplateFactory> loader = ServiceLoader.load(PromptTemplateFactory.class);

        assertThat(loader.stream().map(ServiceLoader.Provider::type))
            .contains(PebbleSafeTemplateFactory.class);
    }

    private PromptTemplateFactory.Template create(String template) {
        return factory.create(new PromptTemplateFactory.Input() {
            public String getTemplate() {
                return template;
            }

            public String getName() {
                return "test";
            }
        });
    }
}
