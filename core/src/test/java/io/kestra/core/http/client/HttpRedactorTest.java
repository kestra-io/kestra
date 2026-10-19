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
    void shouldKeepCorsHeadersButStillRedactSecretValues() {
        Map<String, List<String>> redacted = HttpRedactor.redactHeaders(
            Map.of(
                "Access-Control-Allow-Credentials", List.of("true"),
                "access-control-allow-headers", List.of("Authorization, Content-Type"),
                "Access-Control-Expose-Headers", List.of("s3cr3t-value")
            ),
            Set.of("s3cr3t-value")
        );

        assertThat(redacted.get("Access-Control-Allow-Credentials")).containsExactly("true");
        assertThat(redacted.get("access-control-allow-headers")).containsExactly("Authorization, Content-Type");
        assertThat(redacted.get("Access-Control-Expose-Headers")).containsExactly("***");
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
        var userInfo = String.join(":", "someone", "hunter2");
        URI redacted = HttpRedactor.redactUri(URI.create("https://" + userInfo + "@example.com:8443/path?since=2026&api_key=abc&Token=t#frag"), NO_SECRETS);

        assertThat(redacted).hasToString("https://example.com:8443/path?since=2026&api_key=***&Token=***#frag");
    }

    @Test
    void shouldRedactBareQueryTokenEqualToSecret() {
        URI redacted = HttpRedactor.redactUri(URI.create("https://example.com/notify?my%20secret%21&y=plain"), Set.of("my secret!"));

        assertThat(redacted).hasToString("https://example.com/notify?***&y=plain");
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

    @Test
    void shouldRedactPathSegmentEqualToSecret() {
        URI redacted = HttpRedactor.redactUri(URI.create("https://hooks.example.com/services/s3cr3t-hook/send?x=1"), Set.of("s3cr3t-hook"));

        assertThat(redacted).hasToString("https://hooks.example.com/services/***/send?x=1");
    }

    @Test
    void shouldRedactPathSegmentEqualToDecodedSecret() {
        URI redacted = HttpRedactor.redactUri(URI.create("https://hooks.example.com/a/my%20secret%21"), Set.of("my secret!"));

        assertThat(redacted).hasToString("https://hooks.example.com/a/***");
    }

    @Test
    void shouldRedactMultiSegmentPathEqualToSecret() {
        URI redacted = HttpRedactor.redactUri(URI.create("https://hooks.example.com/services/T000/B000/XXXX"), Set.of("services/T000/B000/XXXX"));

        assertThat(redacted).hasToString("https://hooks.example.com/***");
    }

    @Test
    void shouldRedactWholeUriEqualToSecret() {
        var webhook = "https://hooks.example.com/services/T000/B000/XXXX";

        assertThat(HttpRedactor.redactUri(URI.create(webhook), Set.of(webhook))).hasToString("https://hooks.example.com/***");
        assertThat(HttpRedactor.redactUri(webhook, Set.of(webhook))).isEqualTo("https://hooks.example.com/***");
    }

    @Test
    void shouldNotRedactPathSegmentContainingSecretAsSubstring() {
        URI redacted = HttpRedactor.redactUri(URI.create("https://example.com/prefix-s3cr3t-hook/x"), Set.of("s3cr3t-hook"));

        assertThat(redacted).hasToString("https://example.com/prefix-s3cr3t-hook/x");
    }

    @Test
    void shouldNotRedactPathSegmentEqualToShortSecret() {
        URI redacted = HttpRedactor.redactUri(URI.create("https://example.com/api/v1"), Set.of("api"));

        assertThat(redacted).hasToString("https://example.com/api/v1");
    }

    @Test
    void shouldRedactQueryValueWhoseRawFormIsSecret() {
        URI redacted = HttpRedactor.redactUri(URI.create("https://example.com/p?k=ab+cd==&z=1"), Set.of("ab+cd=="));

        assertThat(redacted).hasToString("https://example.com/p?k=***&z=1");
    }

    @Test
    void shouldRedactCommonCredentialNamesOnWholeTokensOnly() {
        URI redacted = HttpRedactor.redactUri(
            URI.create("https://example.com/p?key=1&sig=2&auth=3&pass=4&pwd=5&access_key=6&accessKey=7&page=8&monkey=9&keyword=10"),
            NO_SECRETS
        );

        assertThat(redacted).hasToString("https://example.com/p?key=***&sig=***&auth=***&pass=***&pwd=***&access_key=***&accessKey=***&page=8&monkey=9&keyword=10");
    }

    @Test
    void shouldRedactUrlValuedHeadersAsUris() {
        var presigned = "https://bucket.example.com/file?X-Amz-Signature=abc&X-Amz-Credential=cred&expires=10";
        Map<String, List<String>> redacted = HttpRedactor.redactHeaders(
            Map.of(
                "Location", List.of(presigned),
                "Content-Location", List.of("/hooks/s3cr3t-hook/x?token=t"),
                "Link", List.of("<https://example.com/page2?api_key=abc>; rel=\"next\", <https://example.com/page3>; rel=\"last\"")
            ),
            Set.of("s3cr3t-hook")
        );

        assertThat(redacted.get("Location")).containsExactly("https://bucket.example.com/file?X-Amz-Signature=***&X-Amz-Credential=***&expires=10");
        assertThat(redacted.get("Content-Location")).containsExactly("/hooks/***/x?token=***");
        assertThat(redacted.get("Link")).containsExactly("<https://example.com/page2?api_key=***>; rel=\"next\", <https://example.com/page3>; rel=\"last\"");
    }

    @Test
    void shouldRedactSegmentAlignedPathSpanCoveredBySlashContainingSecret() {
        var secret = "T000/B000/XXXX";

        assertThat(HttpRedactor.redactUri(URI.create("https://hooks.slack.com/services/T000/B000/XXXX"), Set.of(secret)))
            .hasToString("https://hooks.slack.com/services/***");
        assertThat(HttpRedactor.redactUri(URI.create("https://hooks.slack.com/services/T000%2FB000/XXXX?x=1"), Set.of(secret)))
            .hasToString("https://hooks.slack.com/services/***?x=1");
        assertThat(HttpRedactor.redactUri(URI.create("https://hooks.slack.com/services/T000/B000/XXXXY"), Set.of(secret)))
            .hasToString("https://hooks.slack.com/services/T000/B000/XXXXY");
    }

    @Test
    void shouldKeepPaginationTokensInUrlValuedHeadersButMaskCredentials() {
        Map<String, List<String>> redacted = HttpRedactor.redactHeaders(
            Map.of(
                "Link", List.of("<https://api.example.com/items?page_token=abc&nextPageToken=def&cursor=ghi&access_token=zzz>; rel=\"next\""),
                "Location", List.of("https://api.example.com/items?continuationToken=abc&code=xyz&k=s3cr3t-value")
            ),
            Set.of("s3cr3t-value")
        );

        assertThat(redacted.get("Link")).containsExactly("<https://api.example.com/items?page_token=abc&nextPageToken=def&cursor=ghi&access_token=***>; rel=\"next\"");
        assertThat(redacted.get("Location")).containsExactly("https://api.example.com/items?continuationToken=abc&code=***&k=***");
        assertThat(HttpRedactor.redactUri(URI.create("https://api.example.com/items?page_token=abc"), NO_SECRETS))
            .hasToString("https://api.example.com/items?page_token=***");
    }

    @Test
    void shouldNotTreatIdempotencyKeyAndWebSocketKeyAsSensitive() {
        Map<String, List<String>> redacted = HttpRedactor.redactHeaders(
            Map.of("Idempotency-Key", List.of("abc"), "SEC-WEBSOCKET-KEY", List.of("dGhl"), "X-Cache-Key", List.of("v")),
            NO_SECRETS
        );

        assertThat(redacted.get("Idempotency-Key")).containsExactly("abc");
        assertThat(redacted.get("SEC-WEBSOCKET-KEY")).containsExactly("dGhl");
        assertThat(redacted.get("X-Cache-Key")).containsExactly("***");
        assertThat(HttpRedactor.isSensitiveName("idempotency-key")).isFalse();
    }

    @Test
    void shouldRedactCredentialsInUriFragment() {
        assertThat(HttpRedactor.redactUri(URI.create("https://app.example.com/cb#access_token=abc&token_type=bearer&state=1"), NO_SECRETS))
            .hasToString("https://app.example.com/cb#access_token=***&token_type=***&state=1");
        assertThat(HttpRedactor.redactUri("https://app.example.com/cb#access_token=abc&token_type=bearer&state=1", NO_SECRETS))
            .doesNotContain("abc");
        Map<String, List<String>> redacted = HttpRedactor.redactHeaders(Map.of("Location", List.of("https://app.example.com/cb#access_token=abc&token_type=bearer")), NO_SECRETS);
        assertThat(redacted.get("Location")).containsExactly("https://app.example.com/cb#access_token=***&token_type=bearer");
        assertThat(HttpRedactor.redactUri(URI.create("https://app.example.com/p#s3cr3t-value"), Set.of("s3cr3t-value")))
            .hasToString("https://app.example.com/p#***");
    }
}
