package io.kestra.core.http.client;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpRedactorTest {
    private static final Set<String> NO_SECRETS = Set.of();

    @Test
    void shouldRedactDenyListedHeadersIgnoringCase() {
        Map<String, List<String>> headers = Map.of(
            "AUTHORIZATION", List.of("Bearer abc"),
            "set-cookie", List.of("a=1", "b=2"),
            "X-Api-Key", List.of("key"),
            "Content-Type", List.of("application/json")
        );

        Map<String, List<String>> redacted = HttpRedactor.redactHeaders(headers, NO_SECRETS);

        assertThat(redacted.get("AUTHORIZATION")).containsExactly("***");
        assertThat(redacted.get("set-cookie")).containsExactly("***", "***");
        assertThat(redacted.get("X-Api-Key")).containsExactly("***");
        assertThat(redacted.get("Content-Type")).containsExactly("application/json");
    }

    @Test
    void shouldRedactHeadersMatchingTheNamePattern() {
        Map<String, List<String>> redacted = HttpRedactor.redactHeaders(
            Map.of("X-Session-Token", List.of("t"), "X-My_ApiKey", List.of("k"), "X-Request-Id", List.of("42")),
            NO_SECRETS
        );

        assertThat(redacted.get("X-Session-Token")).containsExactly("***");
        assertThat(redacted.get("X-My_ApiKey")).containsExactly("***");
        assertThat(redacted.get("X-Request-Id")).containsExactly("42");
    }

    @Test
    void shouldRedactHeaderValueEqualToSecretOnly() {
        Map<String, List<String>> redacted = HttpRedactor.redactHeaders(
            Map.of("X-Echo", List.of("s3cr3t-value", "prefix-s3cr3t-value", "ok")),
            Set.of("s3cr3t-value")
        );

        assertThat(redacted.get("X-Echo")).containsExactly("***", "prefix-s3cr3t-value", "ok");
    }

    @Test
    void shouldIgnoreSecretsShorterThanMinimumLength() {
        Map<String, List<String>> redacted = HttpRedactor.redactHeaders(Map.of("X-Echo", List.of("abc")), Set.of("abc"));

        assertThat(redacted.get("X-Echo")).containsExactly("abc");
    }

    @Test
    void shouldStripUserInfoAndRedactSensitiveQueryParams() {
        URI redacted = HttpRedactor.redactUri(URI.create("https://user:pass@example.com:8443/path?since=2026&api_key=abc&Token=t#frag"), NO_SECRETS);

        assertThat(redacted).hasToString("https://example.com:8443/path?since=2026&api_key=***&Token=***#frag");
    }

    @Test
    void shouldRedactQueryValueEqualToDecodedSecret() {
        URI redacted = HttpRedactor.redactUri(URI.create("https://example.com/p?x=my%20secret%21&y=plain"), Set.of("my secret!"));

        assertThat(redacted).hasToString("https://example.com/p?x=***&y=plain");
    }

    @Test
    void shouldKeepRelativeAndQuerylessUrisUntouched() {
        assertThat(HttpRedactor.redactUri("/hello", NO_SECRETS)).isEqualTo("/hello");
        assertThat(HttpRedactor.redactUri(URI.create("http://localhost/a"), NO_SECRETS)).hasToString("http://localhost/a");
    }

    @Test
    void shouldMaskUnparsableLocation() {
        assertThat(HttpRedactor.redactUri("http://bad host/ path", NO_SECRETS)).isEqualTo("***");
    }
}
