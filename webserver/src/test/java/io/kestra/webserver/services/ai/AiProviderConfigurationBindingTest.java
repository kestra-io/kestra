package io.kestra.webserver.services.ai;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers how {@code kestra.ai} reaches the typed provider configuration. Micronaut applies its camel-case key
 * convention at every nesting level of the untyped configuration map, so custom HTTP header names have to be
 * recovered from the raw property source: bound naively, {@code X-Api-Key} arrives as {@code xApiKey}.
 * <p>
 * The {@code providers} configuration is covered end to end, up to the outgoing request, by
 * {@link io.kestra.webserver.services.ai.gemini.GeminiAiServiceTest}.
 */
@KestraTest
// The test environment already declares the legacy single-provider configuration, which only lacks custom headers.
@Property(name = "kestra.ai.gemini.custom-headers.X-Api-Key", value = "secret")
@Property(name = "kestra.ai.gemini.custom-headers.Authorization", value = "Bearer token")
@Property(name = "kestra.ai.providers[0].id", value = AiProviderConfigurationBindingTest.HEADERLESS_PROVIDER_ID)
@Property(name = "kestra.ai.providers[0].type", value = "gemini")
@Property(name = "kestra.ai.providers[0].configuration.model-name", value = "gemini-2.5-flash")
@Property(name = "kestra.ai.providers[0].configuration.api-key", value = "fake-key")
class AiProviderConfigurationBindingTest {
    static final String HEADERLESS_PROVIDER_ID = "gemini-without-headers";

    @Inject
    private AiServiceManager aiServiceManager;

    private AiConfiguration configurationOf(String providerId) {
        return ((AiService<?>) aiServiceManager.getAiService(providerId)).getAiConfiguration();
    }

    @Test
    void shouldKeepCustomHeaderNamesVerbatimForLegacySingleProvider() {
        assertThat(configurationOf("gemini-legacy").customHeaders())
            .isEqualTo(Map.of("X-Api-Key", "secret", "Authorization", "Bearer token"));
    }

    @Test
    void shouldLeaveCustomHeadersUnsetWhenNoneConfigured() {
        assertThat(configurationOf(HEADERLESS_PROVIDER_ID).customHeaders()).isNullOrEmpty();
    }

    @Test
    void shouldCamelCaseProviderPropertiesWrittenInKebabCase() {
        Map<String, Object> normalized = AiServiceManager.normalizeConfigurationKeys(Map.of(
            "model-name", "gemini-2.5-flash",
            "api-key", "fake-key",
            "custom-headers", Map.of("X-Api-Key", "secret")
        ));

        // Only the provider properties are normalized: the nested header names are left alone
        assertThat(normalized).containsEntry("modelName", "gemini-2.5-flash");
        assertThat(normalized).containsEntry("apiKey", "fake-key");
        assertThat(normalized).containsEntry("customHeaders", Map.of("X-Api-Key", "secret"));
    }

    @Test
    void shouldLeaveProviderPropertiesAlreadyInCamelCaseUnchanged() {
        Map<String, Object> normalized = AiServiceManager.normalizeConfigurationKeys(
            Map.of("modelName", "gemini-2.5-flash", "apiKey", "fake-key")
        );

        assertThat(normalized).containsEntry("modelName", "gemini-2.5-flash");
        assertThat(normalized).containsEntry("apiKey", "fake-key");
    }
}
