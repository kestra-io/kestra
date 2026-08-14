package io.kestra.webserver.services.ai;

import io.kestra.core.ai.agent.models.AgentPrincipal;
import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.services.ExpressionContextService;
import io.kestra.core.utils.VersionProvider;
import io.kestra.core.services.InstanceService;
import io.kestra.webserver.services.ai.gemini.FreeTierGeminiAiService;
import io.kestra.webserver.services.posthog.PosthogService;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertyPlaceholderResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The hosted provider that gives an install with no key of its own a working Copilot.
 *
 * <p>The subject is the defaults, so they are asserted rather than trusted: on when nothing is configured, and
 * absent both when switched off and when the operator configured a provider. Each is one configuration line
 * away from being wrong, and being wrong means prompts and flow source going somewhere nobody chose.
 *
 * <p>Mockito rather than {@code @KestraTest} because the module's {@code application-test.yml} configures a
 * Gemini provider — a Micronaut context in this module cannot express "nothing configured", which is the
 * case that matters most here.
 */
@ExtendWith(MockitoExtension.class)
class AiFreeTierTest {
    @Mock
    AiProvidersConfiguration providersConfiguration;
    @Mock
    Environment environment;
    @Mock
    PluginRegistry pluginRegistry;
    @Mock
    JsonSchemaGenerator jsonSchemaGenerator;
    @Mock
    VersionProvider versionProvider;
    @Mock
    InstanceService instanceService;
    @Mock
    PosthogService posthogService;
    @Mock
    NamespaceContextTool namespaceContextTool;
    @Mock
    KestraDocsContextTool kestraDocsContextTool;
    @Mock
    ExpressionContextService expressionContextService;
    @Mock
    io.kestra.core.services.FlowParsingService flowParsingService;

    private static AiFreeTierConfiguration enabled() {
        return new AiFreeTierConfiguration();
    }

    private static AiFreeTierConfiguration disabled() {
        AiFreeTierConfiguration configuration = new AiFreeTierConfiguration();
        configuration.setEnabled(false);
        return configuration;
    }

    @Test
    void shouldRegisterTheHostedProviderWhenNothingIsConfigured() {
        // Given an install that configured no provider at all
        AiServiceManager manager = buildManager(null, enabled());

        // When Copilot asks for a provider
        // Then it gets the hosted one, as the default, rather than a 503 that reads as a broken feature
        assertThat(manager.hasConfiguredProvider()).isTrue();
        assertThat(manager.getDefaultProviderId()).isEqualTo(AiFreeTierConfiguration.PROVIDER_ID);
        assertThat(manager.getAiService(AiFreeTierConfiguration.PROVIDER_ID))
            .isInstanceOf(FreeTierGeminiAiService.class);
    }

    @Test
    void shouldLeaveCopilotUnavailableWhenTheHostedProviderIsDisabled() {
        // Given the free tier switched off, which is what the Enterprise distribution ships
        AiServiceManager manager = buildManager(null, disabled());

        // When Copilot asks for a provider
        // Then nothing is registered, so the request never leaves the deployment. This flag is the mechanism
        // keeping prompts and flow source inside an Enterprise install, and a regression here would be silent:
        // the free tier would simply start working.
        assertThat(manager.hasConfiguredProvider()).isFalse();
        assertThat(manager.getAiService(AiFreeTierConfiguration.PROVIDER_ID)).isNull();
    }

    @Test
    void shouldNotRegisterTheHostedProviderWhenOneIsAlreadyConfigured() {
        // Given an operator who configured their own Gemini key, and the free tier left enabled
        AiProviderConfiguration configured = new AiProviderConfiguration(
            "mine", "Mine", "gemini", true, Map.of("apiKey", "operator-key", "modelName", "gemini-2.5-flash")
        );
        AiServiceManager manager = buildManager(List.of(configured), enabled());

        // When Copilot asks for a provider
        // Then only theirs exists. A fallback that could displace a key someone chose to pay for would be a
        // far worse failure than having no fallback at all.
        assertThat(manager.getAiService(AiFreeTierConfiguration.PROVIDER_ID)).isNull();
        assertThat(manager.getDefaultProviderId()).isEqualTo("mine");
    }

    @Test
    void shouldNotSubstituteTheHostedProviderWhenAConfiguredOneFailsToBuild() {
        // Given a declared provider that cannot be constructed — here a null configuration, which is one of
        // several paths where createAiService returns null rather than throwing
        AiProviderConfiguration broken = new AiProviderConfiguration("mine", "Mine", "gemini", true, null);
        AiServiceManager manager = buildManager(List.of(broken), enabled());

        // When Copilot asks for a provider
        // Then it gets none. The free tier keys off whether a provider was *declared*, not whether one could be
        // built: substituting here would send an operator's prompts and flow source to api.kestra.io because
        // their own provider had a bad configuration, which is a data-flow change nobody asked for.
        assertThat(manager.hasConfiguredProvider()).isFalse();
        assertThat(manager.getAiService(AiFreeTierConfiguration.PROVIDER_ID)).isNull();
    }

    @Test
    void shouldReadTheHostedCeilingFromTheRelayRatherThanTheSynthesizedConfiguration() {
        // Given an operator ceiling on the free-tier configuration, and a relay reporting a different one
        AiFreeTierConfiguration configuration = enabled();
        configuration.setUsageLimit(Map.of("enabled", true, "maxWeight", 1_000_000));

        AiFreeTierLimitProvider limitProvider = mock(AiFreeTierLimitProvider.class);
        when(limitProvider.limit()).thenReturn(Optional.of(new AiUsageLimitConfiguration(
            true, 1.0, 0.1, 6.0, 250_000, 0, 10, AiUsageWindow.DAILY
        )));

        // When the hosted provider is registered
        AiServiceManager manager = buildManager(null, configuration, limitProvider);

        // Then the relay's figure is what the provider reports, not the one copied onto the configuration the
        // manager synthesizes for it. Two routes to one ceiling is how the figure enforced mid-turn stops
        // matching the figure the client was shown; reconciling an operator's own cap against the allowance is
        // the limit provider's job, and is asserted where that decision lives.
        assertThat(manager.getAiService(AiFreeTierConfiguration.PROVIDER_ID).usageLimit().orElseThrow().maxWeight())
            .isEqualTo(250_000);
    }

    @Test
    void shouldClaimNoHostedCeilingWhenTheRelayHasNotBeenReadAtAll() {
        // Given a configured ceiling but nothing able to read the relay
        AiFreeTierConfiguration configuration = enabled();
        configuration.setUsageLimit(Map.of("enabled", true, "maxWeight", 1_000_000));

        // When the hosted provider is registered without one
        AiServiceManager manager = buildManager(null, configuration);

        // Then no ceiling is claimed rather than the synthesized configuration's being used as a second source.
        // Failing open is right here: the relay enforces its own budget and answers 429 when it is spent, so the
        // cost of not knowing the ceiling is a surprising refusal, against refusing turns the relay would serve.
        assertThat(manager.getAiService(AiFreeTierConfiguration.PROVIDER_ID).usageLimit()).isEmpty();
    }

    @Test
    void shouldSendTheInstanceIdentityAndNoUserWhenThereIsNoPrincipal() {
        // Given the hosted provider on an install with no user identity, which is every OSS install
        when(instanceService.fetch()).thenReturn("instance-abc");
        FreeTierGeminiAiService service = freeTierService();

        // When the headers for a turn are assembled with no principal
        Map<String, String> headers = service.identityHeaders(null);

        // Then the instance is named and no user is claimed. The relay meters the instance in that case; a
        // placeholder user would read as a metered user while behaving as a second instance.
        assertThat(headers).containsEntry("X-Kestra-Instance-Id", "instance-abc");
        assertThat(headers).doesNotContainKey("X-Kestra-User-Id");
    }

    @Test
    void shouldSendTheUserIdentityWhenThePrincipalCarriesOne() {
        // Given an edition whose principal names a user, as Enterprise's does
        when(instanceService.fetch()).thenReturn("instance-abc");
        FreeTierGeminiAiService service = freeTierService();
        AgentPrincipal principal = new TestPrincipal("user-42");

        // When the headers for that turn are assembled
        Map<String, String> headers = service.identityHeaders(principal);

        // Then the turn is attributed, so the relay can subdivide the instance's budget per user. The id comes
        // off the principal rather than a request-scoped lookup: a turn need not run on the request thread,
        // and the ambient context would come back empty there while appearing to work.
        assertThat(headers).containsEntry("X-Kestra-Instance-Id", "instance-abc");
        assertThat(headers).containsEntry("X-Kestra-User-Id", "user-42");
    }

    @Test
    void shouldTreatABlankUserIdAsNoUserAtAll() {
        // Given a principal that exists but names no one
        when(instanceService.fetch()).thenReturn("instance-abc");
        FreeTierGeminiAiService service = freeTierService();

        // When the headers are assembled
        Map<String, String> headers = service.identityHeaders(new TestPrincipal("   "));

        // Then the header is omitted rather than sent blank, matching how the relay reads it
        assertThat(headers).doesNotContainKey("X-Kestra-User-Id");
    }

    /** AgentPrincipal has no abstract method — userId() is a default — so a lambda cannot stand in for one. */
    private record TestPrincipal(String userId) implements AgentPrincipal {
    }

    private FreeTierGeminiAiService freeTierService() {
        AiServiceManager manager = buildManager(null, enabled());
        return (FreeTierGeminiAiService) manager.getAiService(AiFreeTierConfiguration.PROVIDER_ID);
    }

    private AiServiceManager buildManager(List<AiProviderConfiguration> providers, AiFreeTierConfiguration freeTier) {
        return buildManager(providers, freeTier, null);
    }

    private AiServiceManager buildManager(
        List<AiProviderConfiguration> providers,
        AiFreeTierConfiguration freeTier,
        AiFreeTierLimitProvider limitProvider
    ) {
        when(providersConfiguration.providers()).thenReturn(providers);
        PropertyPlaceholderResolver placeholderResolver = mock(PropertyPlaceholderResolver.class);
        lenient().when(environment.getPlaceholderResolver()).thenReturn(placeholderResolver);
        lenient().when(placeholderResolver.resolveRequiredPlaceholders(anyString()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        return new AiServiceManager(
            providersConfiguration,
            environment,
            pluginRegistry,
            jsonSchemaGenerator,
            versionProvider,
            instanceService,
            posthogService,
            List.of(),
            namespaceContextTool,
            kestraDocsContextTool,
            expressionContextService,
            flowParsingService,
            freeTier,
            limitProvider
        );
    }
}
