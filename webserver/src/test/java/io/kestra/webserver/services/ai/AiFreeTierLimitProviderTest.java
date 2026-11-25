package io.kestra.webserver.services.ai;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.findUnmatchedRequests;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.moreThan;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The hosted free tier's budget, fetched from the relay rather than configured locally.
 *
 * <p>Served over a real port rather than by a mocked client, because what is under test is that the relay's
 * JSON binds to the instance's configuration type — a wire contract between two separately deployed
 * repositories, which a mock returning a built object would not exercise.
 *
 * <p>Most cases drive {@link AiFreeTierLimitProvider#refresh()} directly, which is the fetch itself and is
 * synchronous. What {@link AiFreeTierLimitProvider#limit()} adds on top — triggering that fetch in the
 * background when the held copy has aged out, and answering from memory meanwhile — has its own cases at the
 * bottom, where the timing is the point.
 */
@WireMockTest
class AiFreeTierLimitProviderTest {
    /** Relative to the configured base URL, exactly as {@link AiFreeTierLimitProvider} composes it. */
    private static final String LIMITS_PATH = "/v1/ai/relay/gemini/limits";

    private static final String FULL_BUDGET = """
        {"enabled":true,"window":"DAILY","maxWeight":400000,"userMaxWeight":250000,\
        "coldInputWeight":1.0,"cachedInputWeight":0.1,"outputWeight":6.0}""";

    private static ReactorHttpClient client;

    @BeforeAll
    static void createClient() {
        // No base URL: the provider composes an absolute one from its configuration, which is the whole point of
        // the base-url property being configurable.
        client = ReactorHttpClient.create(null);
    }

    @AfterAll
    static void closeClient() {
        if (client != null) {
            client.close();
        }
    }

    private static AiFreeTierLimitProvider provider(final String baseUrl) {
        return provider(baseUrl, Duration.ofHours(1));
    }

    private static AiFreeTierLimitProvider provider(final String baseUrl, final Duration refreshInterval) {
        AiFreeTierConfiguration configuration = new AiFreeTierConfiguration();
        configuration.setBaseUrl(baseUrl);
        configuration.setLimitRefreshInterval(refreshInterval);
        return new AiFreeTierLimitProvider(configuration, client);
    }

    private static AiFreeTierLimitProvider providerAgainst(final WireMockRuntimeInfo relay) {
        return provider(relay.getHttpBaseUrl() + "/v1/ai/relay/gemini");
    }

    /** Stubs the relay's {@code /limits} route with the JSON it would serve. */
    private static void relayServes(final String budget) {
        stubFor(get(urlEqualTo(LIMITS_PATH)).willReturn(okJson(budget)));
    }

    /**
     * Asserts the relay was called <em>and</em> answered from the stub. {@code verify} alone is not enough: it
     * records the call whether or not a stub matched, and an unmatched request 404s into the same empty result
     * these cases assert, so a stub that never matched would look identical to a working one.
     */
    private static void assertRelayAnsweredFromTheStub() {
        verify(getRequestedFor(urlEqualTo(LIMITS_PATH)));
        assertThat(findUnmatchedRequests()).isEmpty();
    }

    @Test
    void shouldReportTheRelaysBudgetOnceFetched(WireMockRuntimeInfo relay) {
        // Given a relay serving the budget it enforces
        relayServes(FULL_BUDGET);
        AiFreeTierLimitProvider provider = providerAgainst(relay);

        // When it is refreshed
        provider.refresh();

        // Then the instance holds the relay's own ceilings, rate card and window
        AiUsageLimitConfiguration limit = provider.limit().orElseThrow();
        assertThat(limit.enabled()).isTrue();
        assertThat(limit.maxWeight()).isEqualTo(400_000);
        assertThat(limit.userMaxWeight()).isEqualTo(250_000);
        assertThat(limit.outputWeight()).isEqualTo(6.0);
        assertThat(limit.cachedInputWeight()).isEqualTo(0.1);
        // The name is the contract between the two repositories: a rename on either side leaves every
        // instance reporting no ceiling at all.
        assertThat(limit.window()).isEqualTo(AiUsageWindow.DAILY);
    }

    @Test
    void shouldKeepReadingTheBudgetWhenTheRelayAddsAFieldThisVersionDoesNotKnow(WireMockRuntimeInfo relay) {
        // Given a relay serving a field this instance was built before
        relayServes("""
            {"enabled":true,"window":"DAILY","maxWeight":400000,"someFieldAFutureRelayAdds":"unknown"}""");
        AiFreeTierLimitProvider provider = providerAgainst(relay);

        // When it is refreshed
        provider.refresh();

        // Then the known fields are still read: the server is deployed first, so rejecting an unrecognised
        // field would cost every older instance its budget the day the response is extended
        assertThat(provider.limit().orElseThrow().maxWeight()).isEqualTo(400_000);
    }

    @Test
    void shouldEnforceABudgetTheRelayServesWithoutSayingItIsEnabled(WireMockRuntimeInfo relay) {
        // Given a relay that serves ceilings but no 'enabled' flag
        relayServes("""
            {"window":"DAILY","maxWeight":400000,"userMaxWeight":250000}""");
        AiFreeTierLimitProvider provider = providerAgainst(relay);

        // When it is refreshed
        provider.refresh();

        // Then the budget applies: this arrives through Jackson, not Micronaut's binder, so the @Bindable
        // default does not reach it and an omitted flag must not read as false
        assertThat(provider.limit().orElseThrow().maxWeight()).isEqualTo(400_000);
    }

    @Test
    void shouldReportNoLimitWhenTheRelayServesASwitchedOffOne(WireMockRuntimeInfo relay) {
        // Given a relay that reports a budget it is not currently enforcing
        relayServes("""
            {"enabled":false,"window":"DAILY","maxWeight":400000}""");
        AiFreeTierLimitProvider provider = providerAgainst(relay);

        // When it is refreshed
        provider.refresh();

        // Then it reads as no ceiling, as a declared provider's switched-off limit does
        assertThat(provider.limit()).isEmpty();
        assertRelayAnsweredFromTheStub();
    }

    @Test
    void shouldFallBackToTheDefaultWarningThresholdWhichTheRelayDoesNotServe(WireMockRuntimeInfo relay) {
        // Given a response carrying ceilings and weights but no warning threshold, which is the instance's
        // presentation choice rather than something the relay enforces
        relayServes(FULL_BUDGET);
        AiFreeTierLimitProvider provider = providerAgainst(relay);
        provider.refresh();

        // Then the instance's default applies rather than binding as zero
        assertThat(provider.limit().orElseThrow().warningThresholdPercent()).isEqualTo(10);
    }

    @Test
    void shouldReportNoLimitBeforeTheRelayHasEverBeenReached(WireMockRuntimeInfo relay) {
        // Given a provider that has not fetched yet, as every instance is until its first read returns
        AiFreeTierLimitProvider provider = providerAgainst(relay);

        // Then nothing is shown or enforced — failing open, since the relay enforces its own budget anyway
        assertThat(provider.limit()).isEmpty();
    }

    @Test
    void shouldFetchInTheBackgroundWhenReadRatherThanMakeTheReaderWait(WireMockRuntimeInfo relay) {
        // Given a relay that takes noticeable time to answer
        stubFor(get(urlEqualTo(LIMITS_PATH)).willReturn(okJson(FULL_BUDGET).withFixedDelay(500)));
        AiFreeTierLimitProvider provider = providerAgainst(relay);

        // When the budget is read, as it is before every model call
        // Then the read answers from memory rather than waiting out the relay: a fetch on this path would put
        // api.kestra.io's latency in front of every answer Copilot gives
        assertThat(provider.limit()).isEmpty();

        // and the fetch it started lands on its own
        await().atMost(Duration.ofSeconds(10))
            .untilAsserted(() -> assertThat(provider.limit()).isPresent());
        assertRelayAnsweredFromTheStub();
    }

    @Test
    void shouldNotFetchAgainUntilTheHeldBudgetHasAgedOut(WireMockRuntimeInfo relay) {
        // Given a budget just fetched, and an hour before it is due again
        relayServes(FULL_BUDGET);
        AiFreeTierLimitProvider provider = providerAgainst(relay);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(provider.limit()).isPresent());

        // When read as often as a busy agent turn reads it
        for (int i = 0; i < 100; i++) {
            assertThat(provider.limit()).isPresent();
        }

        // Then the relay was called once, not once per read — this is consulted before every model call, so
        // refreshing on read only works if a read that finds a fresh copy costs nothing
        verify(exactly(1), getRequestedFor(urlEqualTo(LIMITS_PATH)));
    }

    @Test
    void shouldFetchAgainOnceTheHeldBudgetHasAgedOut(WireMockRuntimeInfo relay) {
        // Given a copy that ages out immediately, standing in for one an hour old
        relayServes(FULL_BUDGET);
        AiFreeTierLimitProvider provider = provider(relay.getHttpBaseUrl() + "/v1/ai/relay/gemini", Duration.ZERO);

        // When read repeatedly
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(provider.limit()).isPresent());
        await().atMost(Duration.ofSeconds(10))
            .untilAsserted(() -> {
                provider.limit();
                verify(moreThan(1), getRequestedFor(urlEqualTo(LIMITS_PATH)));
            });

        // Then a re-price or a re-size is picked up without a restart, which is what the refresh is for
        assertThat(provider.limit()).isPresent();
    }

    @Test
    void shouldKeepTheLastKnownBudgetWhenTheRelayCannotBeReached(WireMockRuntimeInfo relay) {
        // Given a provider that has fetched successfully...
        relayServes(FULL_BUDGET);
        AiFreeTierLimitProvider provider = providerAgainst(relay);
        provider.refresh();
        AiUsageLimitConfiguration fetched = provider.limit().orElseThrow();

        // ...and then loses the relay
        AiFreeTierLimitProvider offline = provider("http://localhost:1/v1/ai/relay/gemini");
        offline.refresh();

        // When each is read
        // Then a failed refresh neither throws nor clears what was known: this is read before every model
        // call, so a relay outage must not change what Copilot does
        assertThat(provider.limit()).contains(fetched);
        assertThat(offline.limit()).isEmpty();
    }

    @Test
    void shouldNotThrowWhenTheRelayAnswersSomethingUnexpected(WireMockRuntimeInfo relay) {
        // Given a relay serving a body that is not a budget, as a proxy or an error page would
        stubFor(get(urlEqualTo(LIMITS_PATH))
            .willReturn(okForContentType("text/html", "<html>502 Bad Gateway</html>")));
        AiFreeTierLimitProvider provider = providerAgainst(relay);

        // Then the refresh absorbs it rather than propagating out of a scheduled method
        provider.refresh();
        assertThat(provider.limit()).isEmpty();
        assertRelayAnsweredFromTheStub();
    }
}
