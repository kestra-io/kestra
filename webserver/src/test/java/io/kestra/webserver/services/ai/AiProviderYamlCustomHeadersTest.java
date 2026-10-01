package io.kestra.webserver.services.ai;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the custom HTTP header names of a provider declared in a YAML file, which is how a deployment writes
 * one. A YAML list reaches Micronaut as a single property holding the whole list, not as the flat indexed keys
 * a {@code @Property} produces, so the header names have to survive a different path than the one
 * {@link AiProviderConfigurationBindingTest} and
 * {@link io.kestra.webserver.services.ai.gemini.GeminiAiServiceTest} exercise.
 */
@KestraTest(propertySources = "classpath:ai-providers-custom-headers.yml")
class AiProviderYamlCustomHeadersTest {
    private static final Map<String, String> EXPECTED_HEADERS = Map.of(
        "X-Api-Key", "qa-gateway-secret",
        "X-Gateway-Route", "internal-qa"
    );

    @Inject
    private AiServiceManager aiServiceManager;

    private AiConfiguration configurationOf(String providerId) {
        return ((AiService<?>) aiServiceManager.getAiService(providerId)).getAiConfiguration();
    }

    @Test
    void shouldKeepCustomHeaderNamesVerbatimWhenTheProviderIsDeclaredInYaml() {
        assertThat(configurationOf("gemini-kebab-case-headers").customHeaders()).isEqualTo(EXPECTED_HEADERS);
    }

    @Test
    void shouldKeepCustomHeaderNamesVerbatimWhenTheYamlSpellsThePropertyInCamelCase() {
        assertThat(configurationOf("gemini-camel-case-headers").customHeaders()).isEqualTo(EXPECTED_HEADERS);
    }
}
