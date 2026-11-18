package io.kestra.webserver.services.ai;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(propertySources = "classpath:ai-providers-default.yml")
class AiProviderDefaultKeyTest {
    @Inject
    private AiServiceManager aiServiceManager;

    @Test
    void shouldUseProviderAsDefaultWhenDeclaredWithDefaultKey() {
        assertThat(aiServiceManager.getDefaultProviderId()).isEqualTo("gemini-default");
        assertThat(aiServiceManager.getDefaultAiService()).isSameAs(aiServiceManager.getAiService("gemini-default"));
    }
}
