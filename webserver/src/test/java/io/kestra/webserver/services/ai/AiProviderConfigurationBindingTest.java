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
@Property(name = "kestra.ai.providers[1].id", value = AiProviderConfigurationBindingTest.LIMITED_PROVIDER_ID)
@Property(name = "kestra.ai.providers[1].type", value = "gemini")
@Property(name = "kestra.ai.providers[1].configuration.api-key", value = "fake-key")
@Property(name = "kestra.ai.providers[1].configuration.usage-limit.enabled", value = "true")
@Property(name = "kestra.ai.providers[1].configuration.usage-limit.max-weight", value = "5000000")
@Property(name = "kestra.ai.providers[1].configuration.usage-limit.user-max-weight", value = "250000")
@Property(name = "kestra.ai.providers[1].configuration.usage-limit.output-weight", value = "8.0")
@Property(name = "kestra.ai.providers[1].configuration.usage-limit.window", value = "WEEKLY")
@Property(name = "kestra.ai.providers[2].id", value = AiProviderConfigurationBindingTest.DISABLED_LIMIT_PROVIDER_ID)
@Property(name = "kestra.ai.providers[2].type", value = "gemini")
@Property(name = "kestra.ai.providers[2].configuration.api-key", value = "fake-key")
@Property(name = "kestra.ai.providers[2].configuration.usage-limit.enabled", value = "false")
@Property(name = "kestra.ai.providers[2].configuration.usage-limit.max-weight", value = "5000")
class AiProviderConfigurationBindingTest {
    static final String HEADERLESS_PROVIDER_ID = "gemini-without-headers";
    static final String LIMITED_PROVIDER_ID = "gemini-with-a-ceiling";
    static final String DISABLED_LIMIT_PROVIDER_ID = "gemini-with-a-ceiling-switched-off";

    @Inject
    private AiServiceManager aiServiceManager;

    private AiConfiguration configurationOf(String providerId) {
        return ((AiService<?>) aiServiceManager.getAiService(providerId)).getAiConfiguration();
    }

    @Test
    void shouldBindTheUsageLimitDeclaredOnAProvider() {
        AiUsageLimitConfiguration limit = configurationOf(LIMITED_PROVIDER_ID).usageLimit();

        // The ceiling has to survive the untyped-map round trip, or every configured limit is silently inert.
        assertThat(limit.enabled()).isTrue();
        assertThat(limit.maxWeight()).isEqualTo(5_000_000);
        assertThat(limit.userMaxWeight()).isEqualTo(250_000);
        assertThat(limit.outputWeight()).isEqualTo(8.0);
        assertThat(limit.window()).isEqualTo(AiUsageWindow.WEEKLY);
        assertThat(limit.isEnforceable()).isTrue();
        // unset weights keep their defaults rather than binding as zero, which would weigh input at nothing
        assertThat(limit.coldInputWeight()).isEqualTo(1.0);
        assertThat(limit.cachedInputWeight()).isEqualTo(0.1);
        assertThat(limit.warningThresholdPercent()).isEqualTo(10);
    }

    @Test
    void shouldLeaveTheUsageLimitDisabledWhenAProviderDeclaresNone() {
        // Nothing bound, and nothing to enforce: absence is what a provider reports until a ceiling is set
        assertThat(configurationOf(HEADERLESS_PROVIDER_ID).usageLimit()).isNull();
        assertThat(aiServiceManager.getAiService(HEADERLESS_PROVIDER_ID).usageLimit()).isEmpty();
    }

    @Test
    void shouldReportNoLimitWhenAProviderDeclaresOneAndTurnsItOff() {
        // A ceiling switched off reads as absent rather than as a flag for callers to check
        assertThat(configurationOf(DISABLED_LIMIT_PROVIDER_ID).usageLimit()).isNotNull();
        assertThat(aiServiceManager.getAiService(DISABLED_LIMIT_PROVIDER_ID).usageLimit()).isEmpty();
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
