package io.kestra.webserver.services.ai;

import io.kestra.core.ai.agent.models.AgentPrincipal;
import io.kestra.core.services.InstanceService;
import io.kestra.webserver.services.ai.gemini.FreeTierGeminiAiService;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertyPlaceholderResolver;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * When the hosted provider is registered, and when it is not — the case that decides whether prompts and flow
 * source leave the deployment.
 *
 * <p>Hand-built rather than {@code @KestraTest}: the module's {@code application-test.yml} configures a Gemini
 * provider, so a Micronaut context here cannot express "nothing configured". Only
 * {@code providersConfiguration}, {@code environment} and {@code instanceService} are read; the other mocks
 * just satisfy the constructor and none is stubbed.
 *
 * <p>The relay is not faked — WireMock serves the budget and a real {@link AiFreeTierLimitProvider} reads it.
 */
@ExtendWith(MockitoExtension.class)
@WireMockTest
class AiFreeTierTest {
    /** Relative to the configured base URL, exactly as {@link AiFreeTierLimitProvider} composes it. */
    private static final String LIMITS_PATH = "/v1/ai/relay/gemini/limits";

    private static ReactorHttpClient client;

    @BeforeAll
    static void createClient() {
        client = ReactorHttpClient.create(null);
    }

    @AfterAll
    static void closeClient() {
        if (client != null) {
            client.close();
        }
    }

    /**
     * An instance with no AI configuration: {@link AiServiceManager} reads {@code kestra.ai.type} off this for
     * the legacy single-provider form, and an unstubbed Mockito {@code get} answers {@code Optional.empty()}.
     */
    @Mock
    Environment environment;

    /** Dereferenced while the provider is built ({@code this.instanceUid = instanceService.fetch()}). */
    @Mock
    InstanceService instanceService;

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

        // Then the hosted one is registered as the default, rather than Copilot answering 503
        assertThat(manager.hasConfiguredProvider()).isTrue();
        assertThat(manager.getDefaultProviderId()).isEqualTo(AiFreeTierConfiguration.PROVIDER_ID);
        assertThat(manager.getAiService(AiFreeTierConfiguration.PROVIDER_ID))
            .isInstanceOf(FreeTierGeminiAiService.class);
    }

    @Test
    void shouldLeaveCopilotUnavailableWhenTheHostedProviderIsDisabled() {
        // Given the free tier switched off, as the EE distribution ships it
        AiServiceManager manager = buildManager(null, disabled());

        // Then nothing is registered, so no request leaves the deployment. A regression here is silent — the
        // free tier would simply start working.
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

        // Then only theirs exists: the fallback must never displace a key someone chose to pay for
        assertThat(manager.getAiService(AiFreeTierConfiguration.PROVIDER_ID)).isNull();
        assertThat(manager.getDefaultProviderId()).isEqualTo("mine");
    }

    @Test
    void shouldNotSubstituteTheHostedProviderWhenAConfiguredOneFailsToBuild() {
        // Given a declared provider that cannot be constructed — a null configuration makes createAiService
        // return null rather than throw
        AiProviderConfiguration broken = new AiProviderConfiguration("mine", "Mine", "gemini", true, null);
        AiServiceManager manager = buildManager(List.of(broken), enabled());

        // Then none is registered: the free tier keys off whether a provider was *declared*, so a bad
        // configuration cannot silently redirect prompts and flow source off-instance
        assertThat(manager.hasConfiguredProvider()).isFalse();
        assertThat(manager.getAiService(AiFreeTierConfiguration.PROVIDER_ID)).isNull();
    }

    @Test
    void shouldReadTheHostedCeilingFromTheRelayRatherThanTheSynthesizedConfiguration(WireMockRuntimeInfo relay) {
        // Given a relay reporting a ceiling, read by a real limit provider, so the whole chain from JSON to
        // reported ceiling is under test
        stubFor(get(urlEqualTo(LIMITS_PATH)).willReturn(okJson("""
            {"enabled":true,"window":"DAILY","maxWeight":250000}""")));

        AiFreeTierConfiguration configuration = enabled();
        configuration.setBaseUrl(relay.getHttpBaseUrl() + "/v1/ai/relay/gemini");

        AiFreeTierLimitProvider limitProvider = new AiFreeTierLimitProvider(configuration, client);
        limitProvider.refresh();

        // When the hosted provider is registered
        AiServiceManager manager = buildManager(null, configuration, limitProvider);

        // Then the relay's figure is what the provider reports; the synthesized configuration carries no
        // ceiling, so it must not act as a second source
        assertThat(manager.getAiService(AiFreeTierConfiguration.PROVIDER_ID).usageLimit().orElseThrow().maxWeight())
            .isEqualTo(250_000);
    }

    @Test
    void shouldClaimNoHostedCeilingWhenTheRelayHasNotBeenReadAtAll() {
        // Given nothing able to read the relay
        AiFreeTierConfiguration configuration = enabled();

        // When the hosted provider is registered without one
        AiServiceManager manager = buildManager(null, configuration);

        // Then no ceiling is claimed. Failing open is right: the relay enforces its own budget and answers
        // 429, so inventing one would refuse turns the relay would have served.
        assertThat(manager.getAiService(AiFreeTierConfiguration.PROVIDER_ID).usageLimit()).isEmpty();
    }

    @Test
    void shouldSendTheInstanceIdentityAndNoUserWhenThereIsNoPrincipal() {
        // Given the hosted provider where there is no user identity
        when(instanceService.fetch()).thenReturn("instance-abc");
        FreeTierGeminiAiService service = freeTierService();

        // When the headers for a turn are assembled with no principal
        Map<String, String> headers = service.identityHeaders(null);

        // Then the instance is named and no user is claimed, so the relay meters the instance
        assertThat(headers).containsEntry("X-Kestra-Instance-Id", "instance-abc");
        assertThat(headers).doesNotContainKey("X-Kestra-User-Id");
    }

    @Test
    void shouldSendTheUserIdentityWhenThePrincipalCarriesOne() {
        // Given a principal that names a user, as EE's does
        when(instanceService.fetch()).thenReturn("instance-abc");
        FreeTierGeminiAiService service = freeTierService();
        AgentPrincipal principal = new TestPrincipal("user-42");

        // When the headers for that turn are assembled
        Map<String, String> headers = service.identityHeaders(principal);

        // Then the turn is attributed, so the relay can subdivide the instance budget per user
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
        PropertyPlaceholderResolver placeholderResolver = mock(PropertyPlaceholderResolver.class);
        lenient().when(environment.getPlaceholderResolver()).thenReturn(placeholderResolver);
        lenient().when(placeholderResolver.resolveRequiredPlaceholders(anyString()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        return new AiServiceManager(
            new AiProvidersConfiguration(providers),
            environment,
            null,
            null,
            null,
            instanceService,
            null,
            List.of(),
            null,
            null,
            null,
            null,
            freeTier,
            limitProvider
        );
    }
}
