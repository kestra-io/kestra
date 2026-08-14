package io.kestra.webserver.services.ai;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The hosted free tier's budget, fetched from the relay rather than configured locally.
 *
 * <p>Served by a stand-in relay on a real port rather than a mocked client: what is being checked is that the
 * relay's JSON binds to the instance's own configuration type, and a mock returning an already-built object
 * would assert nothing about the wire contract between two repositories that are deployed separately.
 */
class AiFreeTierLimitProviderTest {
    private static EmbeddedServer relay;
    private static ApplicationContext context;

    @BeforeAll
    static void startStandInRelay() {
        context = ApplicationContext.run(Map.of("stand-in-relay.enabled", "true"), "test");
        relay = context.getBean(EmbeddedServer.class).start();
    }

    @AfterAll
    static void stop() {
        if (context != null) {
            context.close();
        }
    }

    private static AiFreeTierConfiguration configuration(final String baseUrl) {
        AiFreeTierConfiguration configuration = new AiFreeTierConfiguration();
        configuration.setBaseUrl(baseUrl);
        return configuration;
    }

    private AiFreeTierLimitProvider provider(final String baseUrl) {
        return new AiFreeTierLimitProvider(configuration(baseUrl), context.getBean(ReactorHttpClient.class));
    }

    @Test
    void shouldReportTheRelaysBudgetOnceFetched() {
        // Given a relay serving the budget it enforces
        AiFreeTierLimitProvider provider = provider(relay.getURI() + "/stand-in/v1/ai/relay/gemini");

        // When it is refreshed
        provider.refresh();

        // Then the instance holds the relay's own ceilings, rate card and window — none of which an operator
        // could have known, and all of which change when we re-price without any instance being redeployed.
        AiUsageLimitConfiguration limit = provider.limit().orElseThrow();
        assertThat(limit.enabled()).isTrue();
        assertThat(limit.maxWeight()).isEqualTo(2_000_000);
        assertThat(limit.userMaxWeight()).isEqualTo(600_000);
        assertThat(limit.outputWeight()).isEqualTo(6.0);
        assertThat(limit.cachedInputWeight()).isEqualTo(0.1);
        // A day, matching the relay's window: summing a different period would compare this instance's total
        // against a ceiling reckoned over another span, and the figure shown would not be the one enforced.
        assertThat(limit.window()).isEqualTo(Duration.ofDays(1));
    }

    @Test
    void shouldKeepReadingTheBudgetWhenTheRelayAddsAFieldThisVersionDoesNotKnow() {
        // Given a relay serving a field this instance was built before — see the stand-in below
        AiFreeTierLimitProvider provider = provider(relay.getURI() + "/stand-in/v1/ai/relay/gemini");

        // When it is refreshed
        provider.refresh();

        // Then the fields it does understand are read. The two sides are deployed independently and the server
        // moves first, so rejecting an unrecognised field would mean every older instance silently losing its
        // budget the day we extend the response.
        assertThat(provider.limit()).isPresent();
        assertThat(provider.limit().orElseThrow().maxWeight()).isEqualTo(2_000_000);
    }

    @Test
    void shouldFallBackToTheDefaultWarningThresholdWhichTheRelayDoesNotServe() {
        // Given the relay's response, which carries ceilings and weights but no warning threshold — when to warn
        // is a presentation choice belonging to the instance, not a budget the server enforces
        AiFreeTierLimitProvider provider = provider(relay.getURI() + "/stand-in/v1/ai/relay/gemini");
        provider.refresh();

        // Then the instance's own default applies rather than binding as zero, which would have meant a warning
        // that only appears once the allowance is already gone
        assertThat(provider.limit().orElseThrow().warningThresholdPercent()).isEqualTo(10);
    }

    @Test
    void shouldReportNoLimitBeforeTheRelayHasEverBeenReached() {
        // Given a provider that has not refreshed yet — every instance, for its first half minute
        AiFreeTierLimitProvider provider = provider(relay.getURI() + "/stand-in/v1/ai/relay/gemini");

        // Then nothing is shown or enforced. Failing open matters here: the relay enforces its own budget and
        // answers 429, so not knowing the limit costs a user a surprise, where inventing one would refuse turns
        // the relay would have served.
        assertThat(provider.limit()).isEmpty();
    }

    @Test
    void shouldKeepTheLastKnownBudgetWhenTheRelayCannotBeReached() {
        // Given a provider that has fetched successfully...
        AiFreeTierLimitProvider provider = provider(relay.getURI() + "/stand-in/v1/ai/relay/gemini");
        provider.refresh();
        AiUsageLimitConfiguration fetched = provider.limit().orElseThrow();

        // ...and then loses the relay
        AiFreeTierLimitProvider offline = provider("http://localhost:1/v1/ai/relay/gemini");
        offline.refresh();

        // When each is read
        // Then a failed refresh neither throws nor clears what was known. This runs on a schedule and its result
        // is read before every model call, so an outage at api.kestra.io must not change what Copilot does.
        assertThat(provider.limit()).contains(fetched);
        assertThat(offline.limit()).isEmpty();
    }

    @Test
    void shouldNotThrowWhenTheRelayAnswersSomethingUnexpected() {
        // Given a relay serving a body that is not a budget, as a proxy or an error page would
        AiFreeTierLimitProvider provider = provider(relay.getURI() + "/stand-in/nonsense");

        // Then the refresh absorbs it rather than propagating out of a scheduled method
        provider.refresh();
        assertThat(provider.limit()).isEmpty();
    }

    @Test
    void shouldPreferAnOperatorsOwnCeilingOverTheRelays() {
        // Given an operator who capped their instance below the hosted allowance
        AiFreeTierConfiguration configuration = configuration(relay.getURI() + "/stand-in/v1/ai/relay/gemini");
        configuration.setUsageLimit(Map.of("enabled", true, "maxWeight", 50_000));

        AiFreeTierLimitProvider provider =
            new AiFreeTierLimitProvider(configuration, context.getBean(ReactorHttpClient.class));
        provider.refresh();

        // Then theirs wins. Spending less of an allowance than we grant is theirs to decide; spending more is
        // not, and the relay refuses that regardless of what an instance believes.
        assertThat(provider.limit().orElseThrow().maxWeight()).isEqualTo(50_000);
    }

    /**
     * A stand-in for the relay's {@code /limits} route, serving the same JSON api.kestra.io does with its
     * shipped defaults. Test-classpath only, and requires a property nothing else sets, so it cannot answer
     * anywhere but here.
     */
    @Controller("/stand-in")
    @Requires(property = "stand-in-relay.enabled", value = "true")
    static class StandInRelayController {
        @Get("/v1/ai/relay/gemini/limits")
        Map<String, Object> limits() {
            return Map.of(
                "enabled", true,
                "window", "PT24H",
                "maxWeight", 2_000_000,
                "userMaxWeight", 600_000,
                "coldInputWeight", 1.0,
                "cachedInputWeight", 0.1,
                "outputWeight", 6.0,
                // A field this version of the instance knows nothing about. The relay is deployed separately and
                // ahead of instances, so it will add fields; an instance that choked on one would stop reading the
                // ones it does understand.
                "someFieldAFutureRelayAdds", "unknown"
            );
        }

        @Get("/nonsense/limits")
        String nonsense() {
            return "<html>502 Bad Gateway</html>";
        }
    }
}
